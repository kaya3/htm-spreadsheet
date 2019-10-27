void main() {
	int a = 1;
	int b = 0;
	int i = 0;
	while(i < 16) {
		output(i, 0, a);
		int t = a;
		a = a + b;
		b = t;
		i = i + 1;
	}
}
