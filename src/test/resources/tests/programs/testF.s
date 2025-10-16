main:
  lw   x5, 0(x0)
  beq  x0, x0, TAR
  addi x1, x1, 1
  done
TAR:
  sw   x5, 20(x0)
  done

#memset 0x0000, 42
#memset 0x0014, 0
