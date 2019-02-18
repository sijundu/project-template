package scratchpad

import chisel3._
import chisel3.util._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.groundtest.DummyPTW
import freechips.rocketchip.unittest.{UnitTestIO, UnitTest}
import freechips.rocketchip.subsystem.CacheBlockBytes
import freechips.rocketchip.tile.SharedMemoryTLEdge
import freechips.rocketchip.tilelink.{TLRAM, TLXbar, TLFragmenter}

class ScratchpadTestDriver(rowBytes: Int)
    (implicit p: Parameters) extends Module {
  val rowBits = rowBytes * 8
  val io = IO(new Bundle with UnitTestIO {
    val dma = new ScratchpadMemIO(2, 2)
    val read = Vec(2, new ScratchpadReadIO(2, rowBits))
    val write = Vec(2, new ScratchpadWriteIO(2, rowBits))
  })

  val testData = VecInit((0 until 2).map { i =>
    val bytes = (0 until rowBytes).map(j => (i * rowBytes + j).U(8.W))
    Cat(bytes.reverse)
  })

  val (s_start :: s_write :: s_dma_req :: s_dma_resp ::
       s_read :: s_done :: Nil) = Enum(6)
  val state = RegInit(s_start)
  val lastState = RegNext(state)

  val (readRow, readDone) = Counter(state === s_read, 2)
  val readRowReg = RegNext(readRow)

  io.write.zipWithIndex.foreach { case (write, i) =>
    write.en := state === s_write
    write.addr := i.U
    write.data := testData(i)
  }

  io.read.zipWithIndex.foreach { case (read, i) =>
    read.en := state === s_read
    read.addr := readRow

    assert(lastState =/= s_read || read.data === testData(readRowReg),
      s"ScratchpadTest: bank $i read data mismatch")
  }

  val (dmaStage, dmaDone) = Counter(io.dma.resp.fire(), 4)
  val testAddrs = VecInit(4042.U, 8182.U)

  io.dma.req.valid := state === s_dma_req
  io.dma.req.bits.vaddr := testAddrs(dmaStage(0))
  io.dma.req.bits.spbank := dmaStage(0) ^ dmaStage(1)
  io.dma.req.bits.spaddr := dmaStage(0)
  io.dma.req.bits.write := !dmaStage(1)
  io.dma.resp.ready := state === s_dma_resp

  assert(!io.dma.resp.valid || !io.dma.resp.bits.error,
    "ScratchpadTest: DMA request error")

  io.finished := lastState === s_done

  when (state === s_start && io.start) { state := s_write }
  when (state === s_write) { state := s_dma_req }
  when (io.dma.req.fire()) { state := s_dma_resp }
  when (io.dma.resp.fire()) { state := s_dma_req }
  when (dmaDone) { state := s_read }
  when (readDone) { state := s_done }
}

class ScratchpadTest(implicit p: Parameters) extends LazyModule {
  val rowBytes = 64
  val dataBytes = 4
  val maxBytes = 16

  val xbar = LazyModule(new TLXbar)
  val mem = LazyModule(new TLRAM(
    AddressSet(0, 0xffff), beatBytes = dataBytes))
  mem.node := TLFragmenter(dataBytes, maxBytes) := xbar.node

  lazy val edge = xbar.node.edges.out.head
  val tp = p.alterPartial({
    case SharedMemoryTLEdge => edge
    case CacheBlockBytes => maxBytes
  })

  val spad = LazyModule(new Scratchpad(2, 2, 8 * rowBytes,
    maxBytes = maxBytes, dataBits = dataBytes * 8)(tp))
  xbar.node :=* spad.node

  lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle with UnitTestIO)

    val tlb = Module(new FrontendTLB(1, 4)(edge, tp))
    val ptw = Module(new DummyPTW(1)(tp))
    val driver = Module(new ScratchpadTestDriver(rowBytes)(tp))

    ptw.io.requestors(0) <> tlb.io.ptw
    tlb.io.clients(0) <> spad.module.io.tlb
    spad.module.io.read <> driver.io.read
    spad.module.io.write <> driver.io.write
    spad.module.io.dma <> driver.io.dma
    driver.io.start := io.start
    io.finished := driver.io.finished
  }
}

class ScratchpadTestWrapper(implicit p: Parameters) extends UnitTest {
  val test = Module(LazyModule(new ScratchpadTest).module)
  test.io.start := io.start
  io.finished := test.io.finished
}
