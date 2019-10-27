package com.github.kaya3.htmspreadsheet;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.*;

public class ProgramCompiler {
	private final Map<String, FunctionCompiler> functions = new HashMap<>();
	
	public List<Instruction> compile(String src) {
		CompilationUnit unit = JavaParser.parse("class Program { " + src + "\n}");
		ClassOrInterfaceDeclaration cls = unit.getClassByName("Program").get();
		
		for(MethodDeclaration method : cls.getMethods()) {
			String name = method.getNameAsString();
			if(functions.containsKey(name)) {
				throw new IllegalArgumentException("Function " + name + " already exists");
			}
			
			functions.put(name, new FunctionCompiler(this, method));
		}
		
		for(MethodDeclaration method : cls.getMethods()) {
			String name = method.getNameAsString();
			if(name.equals("output") || name.equals("input")) {
				throw new IllegalArgumentException("Function name " + name + " is reserved");
			}
			functions.get(name).compile();
		}
		
		List<Instruction> out = new ArrayList<>();
		
		FunctionCompiler main = functions.get("main");
		if(main.returnsInt() || main.getParamCount() > 0) {
			throw new IllegalArgumentException("main() function must be void with no parameters");
		}
		
		out.addAll(main.compile());
		for(FunctionCompiler f : functions.values()) {
			if(f != main) {
				out.addAll(f.compile());
			}
		}
		
		for(int i = 0; i < out.size();) {
			Instruction instruction = out.get(i);
			instruction.setPos(i);
			if(instruction.getOpcode() == Opcode.NOOP) {
				out.remove(i);
			} else {
				++i;
			}
		}
		return out;
	}
	
	public FunctionCompiler getFunction(String name) {
		return functions.get(name);
	}
}
