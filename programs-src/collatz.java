void main() {
	int n = 1;
	while(n < 11) {
		collatz(n);
		n = n + 1;
	}
}

void collatz(int n) {
	int i = 0;
	int x = n;
	output(i, n, x);
	while(x >= 2) {
		if(x&1) {
			x = 3*x + 1;
		} else {
			x = x >> 1;
		}
		i = i + 1;
		output(i, n, x);
	}
}