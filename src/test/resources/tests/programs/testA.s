main:
  addi x1, x0, 1
  beq  x1, x1, L
  addi x2, x2, 1
  done
L:
  sw   x1, 4(x0)
  done

#memset 0x0004, 0
