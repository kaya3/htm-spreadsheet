void main() {
	output(0, 0, factorial(4));
}

int factorial(int n) {
	if(n == 0) {
		return 1;
	} else {
		return n * factorial(n-1);
	}
}
