main:
  jal  ra, T
  addi x7, x7, 1
  done
T:
  sw   ra, 16(x0)
  done

#memset 0x0010, 0
