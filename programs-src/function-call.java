void main() {
	int a = 1;
	a = foo(a + 3, 6);
	output(0, 0, a);
}

int foo(int u, int v) {
	return u * v;
}
