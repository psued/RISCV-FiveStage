main:
  lw   x10, 0(x0)
  addi x11, x0, 3
  beq  x10, x11, T
  addi x5,  x5,  1
  done
T:
  sw   x10, 8(x0)
  done

#memset 0x0000, 3
#memset 0x0008, 0
