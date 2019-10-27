#!/usr/bin/python3

REGISTER_COUNT = 8
IO_SIZE = 16
PC_REGISTER = 0
BOS_REGISTER = 1
IO_REGISTER = 2
MAX_INT = 2**16 - 1

class State:
	def __init__(self, registers=None, stack=None):
		self.registers = (0,)*REGISTER_COUNT if registers is None else registers
		self.stack = [] if stack is None else stack
	
	def __repr__(self):
		return 'State(registers={!r}, stack={!r})'.format(
			self.registers,
			self.stack
		)
	
	@property
	def program_counter(self):
		return self.registers[PC_REGISTER]
	
	@property
	def base_of_stack(self):
		return self.registers[BOS_REGISTER]

def empty_grid(n):
	return [ [0]*n for i in range(IO_SIZE) ]

class VM:
	def __init__(self, program=None, input_grid=None):
		self.program = [] if program is None else program
		self.state = State()
		self.input_grid = empty_grid(IO_SIZE) if input_grid is None else input_grid
		self.output_grid = empty_grid(IO_SIZE)
	
	@property
	def is_running(self):
		return self.state.program_counter >= 0 and self.state.program_counter < len(self.program)
	
	def step(self, instruction=None):
		if instruction is None and not self.is_running:
			raise ValueError('VM has halted')
		
		op,arg1,arg2 = self.program[self.state.program_counter] if instruction is None else instruction
		
		registers = list(self.state.registers)
		registers[PC_REGISTER] += 1
		
		stack = list(self.state.stack)
		
		if op == 'CONST':
			stack.append(arg1)
		elif op == 'PUSH':
			stack.append(registers[arg1])
		elif op == 'POP':
			registers[arg1] = stack.pop()
		elif op == 'LOAD':
			addr = self.state.base_of_stack + registers[arg2]
			registers[arg1] = stack[addr]
		elif op == 'STORE':
			addr = self.state.base_of_stack + registers[arg2]
			stack[addr] = registers[arg1]
		elif op == 'COPY':
			registers[arg1] = registers[arg2]
		elif op == 'JUMPIF':
			if stack.pop() == 0:
				registers[PC_REGISTER] = registers[arg1]
		elif op == 'NOT':
			x = stack.pop()
			stack.append(~x & MAX_INT)
		else:
			y = stack.pop()
			x = stack.pop()
			if op == 'INPUT':
				registers[IO_REGISTER] = self.input_grid[y][x]
			elif op == 'OUTPUT':
				self.output_grid[y][x] = registers[IO_REGISTER]
			elif op == 'ADD':
				stack.append(x + y)
			elif op == 'MULT':
				stack.append(x * y)
			elif op == 'AND':
				stack.append(x & y)
			elif op == 'OR':
				stack.append(x | y)
			elif op == 'XOR':
				stack.append(x ^ y)
			elif op == 'LSHIFT':
				stack.append((x << y) & MAX_INT)
			elif op == 'RSHIFT':
				stack.append(x >> y)
			elif op == 'EQUALS':
				stack.append(int(x == y))
			elif op == 'LESSTHAN':
				stack.append(int(x < y))
			else:
				raise ValueError('Unknown op: ' + op)
		
		self.state = State(registers, stack)

def parse_line(line):
	line = line.split(';')[0].strip()
	if line:
		parts = line.split(' ') + [0,0]
		return (parts[0], int(parts[1]), int(parts[2]))
	else:
		return None

if __name__ == '__main__':
	program = []
	
	import fileinput
	for line in fileinput.input():
		instruction = parse_line(line)
		if instruction is not None:
			program.append(instruction)
	
	vm = VM(program)
	while vm.is_running:
		vm.step()
	
	print('VM state: {!r}\n'.format(vm.state))
	
	print('Output grid:')
	for row in vm.output_grid:
		print(*row, sep='\t')
