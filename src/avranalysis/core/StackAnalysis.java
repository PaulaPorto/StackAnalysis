package avranalysis.core;


import java.util.HashMap;
import java.util.Map;
import javr.core.AvrDecoder;
import javr.core.AvrInstruction;
import javr.core.AvrInstruction.AbsoluteAddress;
import javr.core.AvrInstruction.RelativeAddress;
import javr.core.AvrInstruction.SBRS;
import javr.io.HexFile;
import javr.memory.ElasticByteMemory;

/**
  * Stack Analysis class.
  *
  * @author paula
  *
  */
public class StackAnalysis {
  /**
 * Contains the raw bytes of the given firmware image being analysed.
 */
  private ElasticByteMemory firmware;

  /**
* The decoder is used for actually decoding an instruction.
*/
  private AvrDecoder decoder = new AvrDecoder();

  /**
 * Records the maximum height seen so far.
 */
  private int maxHeight;
  
  /**
 * Map of instructions that have already been seen and the size of the stack.
 */
  private Map<AvrInstruction, Integer> seenInst = new HashMap<>();

  /**
 * Creates a firmware memory. 
 *
 * @param hf hex file
 */
  public StackAnalysis(HexFile hf) {
    // Create firmware memory
    this.firmware = new ElasticByteMemory();
    // Upload image to firmware memory
    hf.uploadTo(this.firmware);
  }

  /**
 * Apply the stack analysis to the given firmware image producing a maximum
 * stack usage (in bytes).
 *
 * @return maxHeight
 */
  public int apply() {
    // Reset the maximum, height
    this.maxHeight = 0;
    // Traverse instructions starting at beginning
    traverse(0, 0);
    // Return the maximum height observed
    return this.maxHeight;
  }
  
  /**
 * Checks if an instruction has been seen already 
 * and the the stack remains unchanged.
 *
 * @param instruction being checked
 * @param pc Program counter
 * @return boolean bool
 */
  public boolean seen(AvrInstruction instruction, int pc) {
    boolean bool = false;
    if (this.maxHeight == Integer.MAX_VALUE) {
      bool = true;
    }
    for (AvrInstruction i : this.seenInst.keySet()) {
      if (i.toString().equals(instruction.toString())
          && this.seenInst.get(i) == Integer.valueOf(pc))  {
        bool = true;
        if (pc < this.maxHeight) {
          this.maxHeight = Integer.MAX_VALUE;
        }
      }
    }
    this.seenInst.put(instruction, Integer.valueOf(pc));
    return bool;  
  }

  /**
 * Traverse the instruction at a given pc address, assuming the stack has a
 * given height on entry.
 *
 * @param pc
 *            Program Counter of instruction to traverse
 * @param currentHeight
 *            Current height of the stack at this point (in bytes)
 */
  private void traverse(int pc, int currentHeight) {
    // Check whether current stack height is maximum
    this.maxHeight = Math.max(this.maxHeight, currentHeight);
    // Check whether we have terminated or not
    if ((pc * 2) >= this.firmware.size()) {
      // We've gone over end of instruction sequence, so stop.
      return;
    }
    // Process instruction at this address
    AvrInstruction instruction = decodeInstructionAt(pc);
    // Move to the next logical instruction as this is always the starting point.
    int next = pc + instruction.getWidth();
    //
    process(instruction, next, currentHeight);
  }

  /**
 * Process the effect of a given instruction.
 *
 * @param instruction
 *            Instruction to process
 * @param pc
 *            Program counter of following instruction
 * @param currentHeight
 *            Current height of the stack at this point (in bytes)
 */
  private void process(AvrInstruction instruction, int pc, int currentHeight) {
    if (seen(instruction, pc)) {
      return;
    }
    switch (instruction.getOpcode()) {
      case BREQ: {
        RelativeAddress branch = (RelativeAddress) instruction;
        traverse(pc + branch.k, currentHeight);
        traverse(pc, currentHeight);
        //
        break;
      }
      case BRGE: {
        RelativeAddress branch = (RelativeAddress) instruction;
        traverse(pc + branch.k, currentHeight);
        traverse(pc, currentHeight);
        //
        break;
      }
      case BRLT: {
        RelativeAddress branch = (RelativeAddress) instruction;
        traverse(pc + branch.k, currentHeight);
        traverse(pc, currentHeight);
        //
        break;
      }
      case SBRS: {
        SBRS branch = (SBRS) instruction;
        traverse(pc + branch.getWidth(), currentHeight);
        traverse(pc, currentHeight);
        //
        break;
      }
      case CALL: {
        AbsoluteAddress branch = (AbsoluteAddress) instruction;
        if (branch.k != -1) {
          // Explore the branch target
          traverse(pc, currentHeight);
          traverse(branch.k, currentHeight + 2);
        }
        //
        break;
      }
      case RCALL: {
        RelativeAddress branch = (RelativeAddress) instruction;
        if (branch.k != -1) {
          // Explore the branch target
          traverse(pc + branch.k, currentHeight + 2);
        }
        //
        break;    
      }
      case JMP: {
        AbsoluteAddress branch = (AbsoluteAddress) instruction;
        if (branch.k != -1) {
          // Explore the branch target
          traverse(branch.k, currentHeight);
        }
        //
        break;
      }
      case RJMP: {
        RelativeAddress branch = (RelativeAddress) instruction;
        // Check whether infinite loop; if so, terminate.
        if (branch.k != -1) {
          // Explore the branch target
          traverse(pc + branch.k, currentHeight);
        }
        //
        break;
      }
      case RET:
        //
        break;
      case RETI:
        throw new RuntimeException("implement me!"); //$NON-NLS-1$
      case PUSH:
        traverse(pc, currentHeight + 1);
        //
        break;
      case POP:
        traverse(pc, currentHeight - 1);
        //
        break;
      case STS_DATA_WIDE: {
        traverse(pc, currentHeight + 2);
        //
        break;
      }
      default:
        // Indicates a standard instruction where control is transferred to the
        // following instruction.
        traverse(pc, currentHeight);
    }
  }

  /**
 * Decode the instruction at a given PC location.
 *
 * @param pc location
 * @return the decoded instruction at a given PC location
 */
  private AvrInstruction decodeInstructionAt(int pc) {
    return this.decoder.decode(this.firmware, pc);
  }
}
