main:
    addi x11, x11, 1
    lw x10, 0(x0)
    beq x10, x11, func
    addi x11, x11, 1
    done
func:
    sw x10, 4(x0)
    sw x11, 8(x0)
    done
#regset x10, 0
#regset x11, 2
#memset 0x0, 0x3