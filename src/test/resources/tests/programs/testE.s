main:
  jal   ra, func
  addi  x8, x8, 1
  j     .finished

func:
  ret
  addi  x9, x9, 1
  j     .finished

.finished:
  done
