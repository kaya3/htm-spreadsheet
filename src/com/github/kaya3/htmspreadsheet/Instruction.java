package com.github.kaya3.htmspreadsheet;

import java.util.function.Supplier;

public class Instruction {
	public static final int PC_REGISTER = 0;
	public static final int BOS_REGISTER = 1;
	public static final int IO_REGISTER = 2;
	
	public static final int MAX_INT = (1 << 16) - 1;
	
	private int pos = -1;
	private final Opcode opcode;
	private final Supplier<Integer> arg1, arg2;
	
	public Instruction(Opcode opcode) {
		this(opcode, null, null);
	}
	public Instruction(Opcode opcode, int arg1) {
		this(opcode, arg1, null);
	}
	public Instruction(Opcode opcode, Supplier<Integer> arg1) {
		this(opcode, arg1, null);
	}
	public Instruction(Opcode opcode, int arg1, int arg2) {
		this(opcode, () -> arg1, () -> arg2);
	}
	public Instruction(Opcode opcode, Supplier<Integer> arg1, int arg2) {
		this(opcode, arg1, () -> arg2);
	}
	public Instruction(Opcode opcode, int arg1, Supplier<Integer> arg2) {
		this(opcode, () -> arg1, arg2);
	}
	
	public Instruction(Opcode opcode, Supplier<Integer> arg1, Supplier<Integer> arg2) {
		this.opcode = opcode;
		this.arg1 = arg1;
		this.arg2 = arg2;
	}
	
	public int getPos() {
		return pos;
	}
	
	public void setPos(int pos) {
		this.pos = pos;
	}
	
	public Opcode getOpcode() {
		return opcode;
	}
	
	public int getArg1() {
		return arg1 == null ? 0 : arg1.get();
	}
	
	public int getArg2() {
		return arg2 == null ? 0 : arg2.get();
	}
	
	@Override
	public String toString() {
		return arg1 == null
			? opcode.toString()
			: arg2 == null
			? String.format("%s %d", opcode, arg1.get())
			: String.format("%s %d %d", opcode, arg1.get(), arg2.get());
	}
}
