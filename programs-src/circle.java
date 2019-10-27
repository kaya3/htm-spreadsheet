void main() {
	int y = 0;
	while(y < 16) {
		int x = 0;
		while(x < 16) {
			if(inCircle(x,y)) {
				output(x, y, 1);
			}
			x = x + 1;
		}
		y = y + 1;
	}
}

int inCircle(int x, int y) {
	int r = 15*(x+y) - x*x - y*y;
	return r >= 45 && r < 96;
}
