package example

import chisel3._
import chisel3.iotesters._
import chisel3.util._
import org.scalatest._

class PWMBaseTester(pwm: PWMBase) extends PeekPokeTester(pwm) {
  poke(pwm.io.period, 3)
  poke(pwm.io.duty, 2)
  poke(pwm.io.enable, 1)

  step(1)

  var cnt = 0

  for (_ <- 0 until 30) {
    val expected = if (cnt < 2) 1 else 0
    expect(pwm.io.pwmout, expected)
    if (cnt == 2) {
      cnt = 0
    } else {
      cnt += 1
    }
    step(1)
  }
}

class PWMSpec extends FlatSpec with Matchers {
  "PWMBase" should "work" in {
    chisel3.iotesters.Driver(() => new PWMBase(10)) { c =>
      new PWMBaseTester(c)
    }
  }
}
