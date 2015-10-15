/*
 * Spoon - http://spoon.gforge.inria.fr/
 * Copyright (C) 2006 INRIA Futurs <renaud.pawlak@inria.fr>
 *
 * This software is governed by the CeCILL-C License under French law and
 * abiding by the rules of distribution of free software. You can use, modify
 * and/or redistribute the software under the terms of the CeCILL-C license as
 * circulated by CEA, CNRS and INRIA at http://www.cecill.info.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the CeCILL-C License for more details.
 *
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL-C license and that you accept its terms.
 */

package spoon.support.reflect.code;

import spoon.SpoonException;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCase;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLoop;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.code.CtSwitch;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.ParentNotInitializedException;
import spoon.reflect.visitor.CtInheritanceScanner;

import java.util.List;

public abstract class CtStatementImpl extends CtCodeElementImpl implements CtStatement {
	private static final long serialVersionUID = 1L;

	public static void insertAfter(CtStatement target, CtStatement statement)
	throws ParentNotInitializedException {
		CtStatementList sts = target.getFactory().Core().createStatementList();
		sts.addStatement(statement);
		insertAfter(target, sts);
	}

	public static void replace(CtStatement target, CtStatementList statements)
	throws ParentNotInitializedException {
		insertAfter(target, statements);
		CtElement e = target.getParent();
		CtStatementList parentStatementList = (CtStatementList) e;
		parentStatementList.removeStatement(target);
	}

	public static void insertAfter(CtStatement target, CtStatementList statements)
	throws ParentNotInitializedException {
		CtElement e = target.getParent();
		if (e instanceof CtExecutable) {
			throw new RuntimeException("cannot insert in this context (use insertEnd?)");
		}
		CtStatementList parentStatementList = (CtStatementList) e;
		int i = 0;
		for (CtStatement s : parentStatementList.getStatements()) {
			i++;
			if (s == target) {
				break;
			}
		}
		for (int j = statements.getStatements().size() - 1; j >= 0; j--) {
			CtStatement s = statements.getStatements().get(j);
			parentStatementList.getStatements().add(i, s);
		}
	}

	public static void insertBefore(CtStatement target, CtStatement statement)
	throws ParentNotInitializedException {
		CtStatementList sts = target.getFactory().Core().createStatementList();
		sts.addStatement(statement);
		insertBefore(target, sts);
	}

	public static void insertBefore(CtStatement target, CtStatementList statementsToBeInserted)
	throws ParentNotInitializedException {
		CtElement targetParent = target.getParent();
		if (targetParent instanceof CtExecutable) {
			throw new SpoonException("cannot insert in this context (use insertEnd?)");
		}
		if (target.getParent(CtConstructor.class) != null) {
			if (target instanceof CtInvocation
					&& ((CtInvocation<?>) target)
					.getExecutable()
					.getSimpleName()
					.startsWith("<init>")) {
				throw new SpoonException(
						"cannot insert a statement before a super or this invocation.");
			}
		}
		new InsertBeforeVisitor(target, statementsToBeInserted).scan(targetParent);
	}

	private static class InsertBeforeVisitor extends CtInheritanceScanner {
		private CtStatement target;
		private CtStatementList statementsToBeInserted;

		InsertBeforeVisitor(CtStatement target, CtStatementList statementsToBeInserted) {
			this.target = target;
			this.statementsToBeInserted = statementsToBeInserted;
		}

		@Override
		public <R> void visitCtBlock(CtBlock<R> e) {
			super.visitCtBlock(e);

			insertFromFirstStatement(e);
		}

		@Override
		public void visitCtIf(CtIf e) {
			super.visitCtIf(e);

			boolean inThen = true;
			CtStatement stat = e.getThenStatement();
			if (stat != target) {
				stat = e.getElseStatement();
				inThen = false;
			}
			if (stat != target) {
				throw new IllegalArgumentException("should not happen");
			}
			if (stat instanceof CtBlock) {
				insertBegin((CtBlock<?>) stat);
			} else {
				CtBlock<?> block = insertNewBlock(stat);
				if (inThen) {
					e.setThenStatement(block);
				} else {
					e.setElseStatement(block);
				}
			}
		}

		@Override
		public <E> void visitCtSwitch(CtSwitch<E> e) {
			super.visitCtSwitch(e);

			for (CtStatement s : statementsToBeInserted) {
				if (!(s instanceof CtCase)) {
					throw new RuntimeException("cannot insert something that is not case in a switch");
				}
			}

			insertFromLastStatement(e.getCases());
		}

		@Override
		public <E> void visitCtCase(CtCase<E> e) {
			super.visitCtCase(e);

			target.setParent(e);
			insertFromLastStatement(e.getStatements());
		}

		@Override
		public void scanCtLoop(CtLoop loop) {
			super.scanCtLoop(loop);

			CtStatement stat = loop.getBody();
			if (stat instanceof CtBlock) {
				insertBegin((CtBlock<?>) stat);
			} else {
				CtBlock<?> block = insertNewBlock(stat);
				target.setParent(block);
				loop.setBody(block);
			}
		}

		private void insertBegin(CtBlock<?> block) {
			block.insertBegin(statementsToBeInserted);
		}

		private CtBlock<?> insertNewBlock(CtStatement stat) {
			CtBlock<?> block = target.getFactory().Core().createBlock();
			block.addStatement(stat);
			insertFromFirstStatement(block);
			return block;
		}

		private void insertFromFirstStatement(CtBlock<?> block) {
			int indexOfTargetElement = 0;
			for (CtStatement s : block.getStatements()) {
				if (s == target) {
					break;
				}
				indexOfTargetElement++;
			}
			for (CtStatement s : statementsToBeInserted) {
				s.setParent(block);
				block.getStatements().add(indexOfTargetElement++, s);
			}
		}

		private <T extends CtElement> void insertFromLastStatement(List<T> statements) {
			int i = 0;
			for (T s : statements) {
				if (s == target) {
					break;
				}
				i++;
			}
			for (int j = statementsToBeInserted.getStatements().size() - 1; j >= 0; j--) {
				final CtStatement newStatement = statementsToBeInserted.getStatements().get(j);
				newStatement.setParent(statements.get(i).getParent());
				statements.add(i, (T) newStatement);
			}
		}
	}

	@Override
	public <T extends CtStatement> T insertBefore(CtStatement statement) throws ParentNotInitializedException {
		insertBefore(this, statement);
		return (T) this;
	}

	@Override
	public <T extends CtStatement> T insertBefore(CtStatementList statements) throws ParentNotInitializedException {
		insertBefore(this, statements);
		return (T) this;
	}

	@Override
	public <T extends CtStatement> T insertAfter(CtStatement statement) throws ParentNotInitializedException {
		insertAfter(this, statement);
		return (T) this;
	}

	@Override
	public <T extends CtStatement> T insertAfter(CtStatementList statements) throws ParentNotInitializedException {
		insertAfter(this, statements);
		return (T) this;
	}

	@Override
	public void replace(CtElement element) {
		if (element instanceof CtStatementList) {
			CtStatementImpl.replace(this, (CtStatementList) element);
		} else {
			super.replace(element);
		}
	}

	String label;

	@Override
	public String getLabel() {
		return label;
	}

	@Override
	public <T extends CtStatement> T setLabel(String label) {
		this.label = label;
		return (T) this;
	}

	@Override
	public void replace(CtStatement element) {
		replace((CtElement) element);
	}
}
