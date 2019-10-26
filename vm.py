#!/usr/bin/python3

REGISTER_COUNT = 8
IO_SIZE = 9
PC_REGISTER = 0
BOS_REGISTER = 1
IO_REGISTER = 2
MAX_INT = 2**16 - 1

class State:
	def __init__(self, registers=None, top_of_stack=0, stack=None):
		self.registers = (0,)*REGISTER_COUNT if registers is None else registers
		self.top_of_stack = top_of_stack
		self.stack = [] if stack is None else stack
	
	def __repr__(self):
		return 'State(registers={!r}, top_of_stack={!r}, stack={!r})'.format(
			self.registers,
			self.top_of_stack,
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
		
		top_of_stack = self.state.top_of_stack
		stack = list(self.state.stack)
		
		if op == 'CONST':
			registers[arg1] = arg2
		elif op == 'PUSH':
			stack.append(registers[arg1])
			top_of_stack += 1
		elif op == 'POP':
			registers[arg1] = stack.pop()
			top_of_stack -= 1
		elif op == 'LOAD':
			addr = self.state.base_of_stack + registers[arg2]
			registers[arg1] = stack[addr]
		elif op == 'STORE':
			addr = self.state.base_of_stack + registers[arg2]
			stack[addr] = registers[arg1]
		elif op == 'COPY':
			registers[arg1] = registers[arg2]
		elif op == 'INPUT':
			x = registers[arg1]
			y = registers[arg2]
			registers[IO_REGISTER] = self.input_grid[y][x]
		elif op == 'OUTPUT':
			x = registers[arg1]
			y = registers[arg2]
			self.output_grid[y][x] = registers[IO_REGISTER]
		elif op == 'JUMPIF':
			if registers[arg2] == 0:
				registers[PC_REGISTER] = registers[arg1]
		elif op == 'ADD':
			registers[arg1] = (registers[arg1] + registers[arg2]) & MAX_INT
		elif op == 'MULT':
			registers[arg1] = (registers[arg1] * registers[arg2]) & MAX_INT
		elif op == 'AND':
			registers[arg1] = registers[arg1] & registers[arg2]
		elif op == 'OR':
			registers[arg1] = registers[arg1] | registers[arg2]
		elif op == 'LSHIFT':
			registers[arg1] = (registers[arg1] << registers[arg2]) & MAX_INT
		elif op == 'RSHIFT':
			registers[arg1] = registers[arg1] >> registers[arg2]
		elif op == 'EQUALS':
			registers[arg1] = int(registers[arg1] == registers[arg2])
		elif op == 'LESSTHAN':
			registers[arg1] = int(registers[arg1] < registers[arg2])
		elif op == 'NOT':
			registers[arg1] = (~registers[arg1]) & MAX_INT
		else:
			raise ValueError('Unknown op: ' + op)
		
		self.state = State(registers, top_of_stack, stack)

def parse_line(line):
	line = line.split(';')[0].strip()
	if line:
		parts = line.split(' ') + [0]
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
