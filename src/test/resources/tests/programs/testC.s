main:
  addi x5, x0, 7
  addi x5, x5, 1
  beq  x5, x5, L
  addi x6, x6, 1
  done
L:
  sw   x5, 12(x0)
  done

#memset 0x000C, 0
