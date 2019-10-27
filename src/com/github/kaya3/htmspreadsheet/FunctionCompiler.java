package com.github.kaya3.htmspreadsheet;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class FunctionCompiler {
	private static void assertInt(Type type) {
		if(!type.toString().equals("int")) {
			throw new IllegalArgumentException("Invalid type " + type);
		}
	}
	private static void invalidAST(Node node) {
		throw new IllegalArgumentException("Invalid AST node: " + node.getClass().getSimpleName() + "\n" + node);
	}
	
	private final ProgramCompiler compiler;
	private final MethodDeclaration method;
	
	private final int paramCount;
	private final boolean returnsInt;
	private final List<String> variables = new ArrayList<>();
	
	private List<Instruction> out;
	private Instruction startNoop, returnNoop;
	private int stackDepth;
	
	public FunctionCompiler(ProgramCompiler compiler, MethodDeclaration method) {
		if(!method.getModifiers().isEmpty() || !method.getTypeParameters().isEmpty() || !method.getThrownExceptions().isEmpty()) {
			throw new IllegalArgumentException("Method declaration must not have modifiers, type parameters or declared exceptions");
		} else if(method.getNameAsString().equals("print")) {
			throw new IllegalArgumentException("");
		}
		
		this.compiler = compiler;
		this.method = method;
		
		paramCount = method.getParameters().size();
		for(Parameter p : method.getParameters()) {
			addVariable(p.getNameAsString());
		}
		
		method.getBody().get().accept(new VoidVisitorAdapter<Void>() {
			@Override
			public void visit(VariableDeclarationExpr n, Void v) {
				assertInt(n.getElementType());
				if(n.getVariables().size() != 1) {
					throw new IllegalArgumentException("Cannot declare multiple variables at once: " + n);
				} else if(!n.getModifiers().isEmpty()) {
					throw new IllegalArgumentException("Variable declaration cannot have modifiers: " + n);
				} else if(!n.getVariable(0).getInitializer().isPresent()) {
					throw new IllegalArgumentException("Variable declaration must have initialiser: " + n);
				}
				addVariable(n.getVariable(0).getNameAsString());
			}
			@Override
			public void visit(AssignExpr n, Void v) {
				if(n.getOperator() != AssignExpr.Operator.ASSIGN) {
					throw new IllegalArgumentException("Illegal assignment operator " + n.getOperator());
				}
			}
			@Override
			public void visit(MethodCallExpr n, Void v) {
				if(n.getScope().isPresent()) {
					throw new IllegalArgumentException("Method call cannot have scope: " + n);
				} else if(n.getTypeArguments().isPresent() && !n.getTypeArguments().get().isEmpty()) {
					throw new IllegalArgumentException("Method call cannot have type arguments: " + n);
				}
			}
		}, null);
		
		String typeName = method.getType().toString();
		if(typeName.equals("int")) {
			returnsInt = true;
		} else if(typeName.equals("void")) {
			returnsInt = false;
		} else {
			throw new IllegalArgumentException("Invalid return type " + typeName + " for function " + method.getNameAsString());
		}
	}
	
	public int getParamCount() {
		return paramCount;
	}
	
	public int getVariableCount() {
		return variables.size();
	}
	
	public boolean returnsInt() {
		return returnsInt;
	}
	
	public List<Instruction> compile() {
		stackDepth = paramCount;
		out = new ArrayList<>();
		// for jumping to
		emit(startNoop = new Instruction(Opcode.NOOP));
		
		for(int i = paramCount-1; i >= 0; --i) {
			emitPopToVar(variables.get(i));
		}
		compileBlockStmt(method.getBody().get());
		
		emit(returnNoop = new Instruction(Opcode.NOOP));
		emitReturn();
		return out;
	}
	
	public int getStartPos() {
		return startNoop.getPos();
	}
	private int getReturnPos() {
		return returnNoop.getPos();
	}
	
	private void compileStmt(Statement s) {
		if(s instanceof BlockStmt) {
			compileBlockStmt((BlockStmt) s);
		} else if(s instanceof ExpressionStmt) {
			Expression e = ((ExpressionStmt) s).getExpression();
			// assignment or function call (including print)
			if(e instanceof VariableDeclarationExpr) {
				VariableDeclarator v = ((VariableDeclarationExpr) e).getVariable(0);
				compileAssignmentStmt(v.getName().asString(), v.getInitializer().get());
			} else if(e instanceof MethodCallExpr) {
				MethodCallExpr ex = (MethodCallExpr) e;
				String name = ex.getNameAsString();
				if(name.equals("output")) {
					compileOutputStmt(ex.getArguments());
				} else if(name.equals("input")) {
					throw new IllegalArgumentException("input(...) call cannot be statement");
				} else {
					compileFunctionCall(name, ex.getArguments(), false);
				}
			} else if(e instanceof AssignExpr) {
				AssignExpr ex = (AssignExpr) e;
				compileAssignmentStmt(ex.getTarget().toString(), ex.getValue());
			} else {
				invalidAST(e);
			}
		} else if(s instanceof IfStmt) {
			compileIfStmt((IfStmt) s);
		} else if(s instanceof ReturnStmt) {
			compileReturnStmt((ReturnStmt) s);
		} else if(s instanceof WhileStmt) {
			compileWhileStmt((WhileStmt) s);
		} else {
			invalidAST(s);
		}
	}
	
	private void compileBlockStmt(BlockStmt blockStmt) {
		for(Statement s : blockStmt.getStatements()) {
			compileStmt(s);
		}
	}
	
	private void compileIfStmt(IfStmt s) {
		compileExpr(s.getCondition());
		Instruction ifEnd = new Instruction(Opcode.NOOP);
		emitJumpIf(ifEnd);
		compileStmt(s.getThenStmt());
		if(!s.hasElseBlock()) {
			emit(ifEnd);
		} else {
			Instruction elseEnd = new Instruction(Opcode.NOOP);
			emitJump(elseEnd::getPos);
			emit(ifEnd);
			compileStmt(s.getElseStmt().get());
			emit(elseEnd);
		}
	}
	
	private void compileWhileStmt(WhileStmt s) {
		Instruction whileStart = new Instruction(Opcode.NOOP);
		Instruction whileEnd = new Instruction(Opcode.NOOP);
		emit(whileStart);
		compileExpr(s.getCondition());
		emitJumpIf(whileEnd);
		compileStmt(s.getBody());
		emitJump(whileStart::getPos);
		emit(whileEnd);
	}
	
	private void compileAssignmentStmt(String s, Expression rhs) {
		compileExpr(rhs);
		emitPopToVar(s);
	}
	
	private void compileOutputStmt(NodeList<Expression> arguments) {
		if(arguments.size() != 3) {
			throw new IllegalArgumentException("output(x,y,v) must take three arguments");
		}
		
		compileExpr(arguments.get(0));
		compileExpr(arguments.get(1));
		compileExpr(arguments.get(2));
		emit(new Instruction(Opcode.POP, Instruction.IO_REGISTER));
		emit(new Instruction(Opcode.OUTPUT));
	}
	
	private void compileInputExpr(NodeList<Expression> arguments) {
		if(arguments.size() != 2) {
			throw new IllegalArgumentException("input(x,y) must take two arguments");
		}
		
		compileExpr(arguments.get(0));
		compileExpr(arguments.get(1));
		emit(new Instruction(Opcode.INPUT));
		emit(new Instruction(Opcode.PUSH, Instruction.IO_REGISTER));
	}
	
	private void compileFunctionCall(String name, NodeList<Expression> arguments, boolean expr) {
		FunctionCompiler f = compiler.getFunction(name);
		if(f == null) {
			throw new IllegalArgumentException("No such function: " + name);
		} else if(expr && !f.returnsInt()) {
			throw new IllegalArgumentException("Function " + name + " is void, cannot be expression");
		} else if(arguments.size() != f.getParamCount()) {
			throw new IllegalArgumentException("Wrong number of arguments for function " + name + "; expected " + f.getParamCount() + ", was " + arguments.size());
		}
		
		int maxVarRegister = 2 + variables.size();
		// push registers
		for(int i = maxVarRegister; i >= 3; --i) {
			emit(new Instruction(Opcode.PUSH, i));
		}
		
		// save PC and BOS so can restore after returning
		Instruction returnTo = new Instruction(Opcode.NOOP);
		emit(new Instruction(Opcode.CONST, returnTo::getPos));
		emit(new Instruction(Opcode.PUSH, Instruction.BOS_REGISTER));
		
		// update BOS
		int bosOffset = stackDepth;
		
		// push arguments
		for(Expression arg : arguments) {
			compileExpr(arg);
		}
		
		emit(new Instruction(Opcode.PUSH, Instruction.BOS_REGISTER));
		emit(new Instruction(Opcode.CONST, bosOffset));
		emit(new Instruction(Opcode.ADD));
		emit(new Instruction(Opcode.POP, Instruction.BOS_REGISTER));
		
		// jump to f
		emitJump(f::getStartPos);
		
		emit(returnTo);
		// PC and BOS restored by returner
		stackDepth -= 3;
		
		// restore registers
		for(int i = 3; i <= maxVarRegister; ++i) {
			emit(new Instruction(Opcode.POP, i));
		}
		
		if(expr) {
			// return value left in IO register; push to stack
			emit(new Instruction(Opcode.PUSH, Instruction.IO_REGISTER));
		}
	}
	
	private void compileReturnStmt(ReturnStmt s) {
		if(s.getExpression().isPresent()) {
			if(!returnsInt) {
				throw new IllegalArgumentException("Cannot return value from void function: " + s);
			}
			compileExpr(s.getExpression().get());
			emit(new Instruction(Opcode.POP, Instruction.IO_REGISTER));
		} else if(returnsInt) {
			throw new IllegalArgumentException("Non-void function must return a value");
		}
		emitJump(this::getReturnPos);
	}
	
	private void emitReturn() {
		String name = method.getNameAsString();
		if(name.equals("main")) {
			emitHalt();
		} else if(stackDepth != 0) {
			throw new IllegalArgumentException("Incorrect stack depth " + stackDepth + " at return point in " + name + "; expected 0");
		} else {
			emit(new Instruction(Opcode.POP, Instruction.BOS_REGISTER));
			emit(new Instruction(Opcode.POP, Instruction.PC_REGISTER));
		}
	}
	
	private void compileExpr(Expression e) {
		if(e instanceof EnclosedExpr) {
			compileExpr(((EnclosedExpr) e).getInner().get());
		} else if(e instanceof BinaryExpr) {
			BinaryExpr ex = (BinaryExpr) e;
			BinaryExpr.Operator o = ex.getOperator();
			if(o == BinaryExpr.Operator.AND) {
				compileShortCircuitAnd(ex);
			} else if(o == BinaryExpr.Operator.OR) {
				compileShortCircuitOr(ex);
			} else {
				compileBinaryExpr((BinaryExpr) e);
			}
		} else if(e instanceof UnaryExpr) {
			compileUnaryExpr((UnaryExpr) e);
		} else if(e instanceof IntegerLiteralExpr) {
			compileLiteralExpr((IntegerLiteralExpr) e);
		} else if(e instanceof MethodCallExpr) {
			MethodCallExpr ex = (MethodCallExpr) e;
			String name = ex.getNameAsString();
			if(name.equals("output")) {
				throw new IllegalArgumentException("output(...) call cannot be expression");
			} else if(name.equals("input")) {
				compileInputExpr(ex.getArguments());
			} else {
				compileFunctionCall(name, ex.getArguments(), true);
			}
		} else if(e instanceof NameExpr) {
			emitPushVar(((NameExpr) e).getNameAsString());
		} else {
			invalidAST(e);
		}
	}
	
	private void compileShortCircuitAnd(BinaryExpr e) {
		Instruction halfway = new Instruction(Opcode.NOOP);
		Instruction end = new Instruction(Opcode.NOOP);
		
		compileExpr(e.getLeft());
		emitJumpIf(halfway);
		compileExpr(e.getRight());
		emitJump(end::getPos);
		emit(halfway);
		emit(new Instruction(Opcode.CONST, 0));
		emit(end);
		stackDepth--;
	}
	
	private void compileShortCircuitOr(BinaryExpr e) {
		Instruction halfway = new Instruction(Opcode.NOOP);
		Instruction end = new Instruction(Opcode.NOOP);
		
		compileExpr(e.getLeft());
		emitJumpIf(halfway);
		emit(new Instruction(Opcode.CONST, 1));
		emitJump(end::getPos);
		emit(halfway);
		compileExpr(e.getRight());
		emit(end);
		stackDepth--;
	}
	
	private void compileBinaryExpr(BinaryExpr e) {
		compileExpr(e.getLeft());
		compileExpr(e.getRight());
		switch(e.getOperator()) {
			case BINARY_OR:
				emit(new Instruction(Opcode.OR));
				break;
			case BINARY_AND:
				emit(new Instruction(Opcode.AND));
				break;
			case XOR:
				emit(new Instruction(Opcode.XOR));
				break;
			case EQUALS:
				emit(new Instruction(Opcode.EQUALS));
				break;
			case NOT_EQUALS:
				emit(new Instruction(Opcode.EQUALS));
				emitLogicalNot();
				break;
			case LESS_EQUALS:
				emitAddOne();
			case LESS:
				emit(new Instruction(Opcode.LESSTHAN));
				break;
			case GREATER:
				emitAddOne();
			case GREATER_EQUALS:
				emit(new Instruction(Opcode.LESSTHAN));
				emitLogicalNot();
				break;
			case LEFT_SHIFT:
				emit(new Instruction(Opcode.LSHIFT));
				break;
			case SIGNED_RIGHT_SHIFT:
				emit(new Instruction(Opcode.RSHIFT));
				break;
			case MINUS:
				emitUnaryMinus();
			case PLUS:
				emit(new Instruction(Opcode.ADD));
				break;
			case MULTIPLY:
				emit(new Instruction(Opcode.MULT));
				break;
			default:
				throw new IllegalArgumentException("Invalid binary operator: " + e.getOperator());
		}
	}
	
	private void compileUnaryExpr(UnaryExpr e) {
		compileExpr(e.getExpression());
		switch(e.getOperator()) {
			case MINUS:
				emitUnaryMinus();
				break;
			case LOGICAL_COMPLEMENT:
				emitLogicalNot();
				break;
			case BITWISE_COMPLEMENT:
				emit(new Instruction(Opcode.NOT));
				break;
			default:
				throw new IllegalArgumentException("Invalid unary operator: " + e.getOperator());
		}
	}
	
	private void emitAddOne() {
		emit(new Instruction(Opcode.CONST, 1));
		emit(new Instruction(Opcode.ADD));
	}
	
	private void emitUnaryMinus() {
		emit(new Instruction(Opcode.CONST, -1));
		emit(new Instruction(Opcode.MULT));
	}
	
	private void emitLogicalNot() {
		emit(new Instruction(Opcode.CONST, 0));
		emit(new Instruction(Opcode.EQUALS));
	}
	
	private void compileLiteralExpr(IntegerLiteralExpr e) {
		int x = e.asInt();
		if(x < -Instruction.MAX_INT || x > Instruction.MAX_INT) {
			throw new IllegalArgumentException("Integer literal " + x + " too large");
		}
		emit(new Instruction(Opcode.CONST, x & Instruction.MAX_INT));
	}
	
	private void emitJump(Supplier<Integer> toPos) {
		emit(new Instruction(Opcode.CONST, toPos));
		emit(new Instruction(Opcode.POP, Instruction.PC_REGISTER));
	}
	
	private void emitJumpIf(Instruction to) {
		emit(new Instruction(Opcode.CONST, to::getPos));
		emit(new Instruction(Opcode.POP, Instruction.IO_REGISTER));
		emit(new Instruction(Opcode.JUMPIF, Instruction.IO_REGISTER));
	}
	
	private void emitHalt() {
		emitJump(() -> -1);
	}
	
	private void addVariable(String name) {
		// TODO: allow more than 5 variables using STORE/LOAD
		
		if(variables.contains(name)) {
			throw new IllegalArgumentException("Variable " + name + " already declared");
		}
		variables.add(name);
		if(variables.size() > 5) {
			throw new IllegalArgumentException("Too many variables (max 5 per function)");
		}
	}
	
	private int getVariableRegister(String name) {
		int idx = variables.indexOf(name);
		if(idx < 0) {
			throw new IllegalArgumentException("No such variable " + name);
		}
		return idx + 3;
	}
	
	private void emitPushVar(String name) {
		int reg = getVariableRegister(name);
		emit(new Instruction(Opcode.PUSH, reg));
	}
	
	private void emitPopToVar(String name) {
		int reg = getVariableRegister(name);
		emit(new Instruction(Opcode.POP, reg));
	}
	
	private void emit(Instruction instruction) {
		int n = out.size();
		if(instruction.getOpcode() == Opcode.POP && n > 0 && out.get(n-1).getOpcode() == Opcode.PUSH) {
			int toReg = instruction.getArg1();
			int fromReg = out.remove(n-1).getArg1();
			if(toReg != fromReg) {
				out.add(new Instruction(Opcode.COPY, toReg, fromReg));
			}
		} else {
			out.add(instruction);
		}
		stackDepth += instruction.getOpcode().getStackDelta();
	}
}
