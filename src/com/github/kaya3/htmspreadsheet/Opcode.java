package com.github.kaya3.htmspreadsheet;

public enum Opcode {
	NOOP, CONST(1),
	PUSH(1), POP(-1), LOAD, STORE,
	COPY,
	INPUT(-2), OUTPUT(-2),
	JUMPIF(-1),
	ADD(-1), MULT(-1), AND(-1), OR(-1), XOR(-1), LSHIFT(-1), RSHIFT(-1),
	NOT,
	EQUALS(-1), LESSTHAN(-1);
	
	private final int stackDelta;
	Opcode() {
		this(0);
	}
	Opcode(int stackDelta) {
		this.stackDelta = stackDelta;
	}
	
	public int getStackDelta() {
		return stackDelta;
	}
}
