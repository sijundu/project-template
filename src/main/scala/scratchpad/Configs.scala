package scratchpad

import chisel3._
import freechips.rocketchip.subsystem.BaseSubsystemConfig
import freechips.rocketchip.config.{Parameters, Config}
import freechips.rocketchip.rocket.{RocketCoreParams}
import freechips.rocketchip.tile.{TileKey, RocketTileParams}
import freechips.rocketchip.unittest.UnitTests

class WithScratchpadUnitTests extends Config((site, here, up) => {
  case TileKey => RocketTileParams(
    core = RocketCoreParams(nPMPs = 0))
  case UnitTests => (p: Parameters) =>
    Seq(Module(new ScratchpadTestWrapper()(p)))
})

class ScratchpadUnitTestConfig extends Config(
  new WithScratchpadUnitTests ++ new BaseSubsystemConfig)
