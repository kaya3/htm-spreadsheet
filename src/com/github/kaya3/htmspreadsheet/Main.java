package com.github.kaya3.htmspreadsheet;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Main {
	public static final String SRC_FILENAME = "programs-src/fibonacci-recursion.java";
	
	public static void main(String[] args) throws IOException {
		String src = readSourceFile(SRC_FILENAME);
		
		ProgramCompiler compiler = new ProgramCompiler();
		for(Instruction instruction : compiler.compile(src)) {
			System.out.println(instruction);
		}
	}
	
	private static String readSourceFile(String filename) throws IOException {
		StringBuilder src = new StringBuilder();
		BufferedReader file = new BufferedReader(new FileReader(filename));
		String line;
		while((line = file.readLine()) != null) {
			src.append(line);
		}
		return src.toString();
	}
}
