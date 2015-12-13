
package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import nachos.vm.VMKernel.PID;

import java.util.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
		vmKernel = (VMKernel)Kernel.kernel;
		for(int i=0;i<64;i++)
			pageTable[i].valid = false;
	}



	private void synchronize(){
	for(TranslationEntry entry:pageTable){
		TranslationEntry e = vmKernel.checkAddress(super.processID(),entry.vpn);
		if(e.valid==false||e==null)
			entry.valid=false;
		else if(e!=null)
			e.valid=true;
	}
	}

	private void sync(TranslationEntry e){
		for(TranslationEntry entry: pageTable){
			if(e.vpn == entry.vpn && e.ppn == entry.ppn){
				entry.valid = e.valid;
				entry.readOnly = e.readOnly;
				entry.dirty = e.dirty;
				entry.used = e.used;
			}
		}
	}
	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		Processor processor = Machine.processor();
		TranslationEntry TLBEntry;
		for(int i = 0; i < processor.getTLBSize(); i++)
		{
			TLBEntry = processor.readTLBEntry(i);
			if(TLBEntry.valid){
				TLBEntry.valid = false;
				processor.writeTLBEntry(i, TLBEntry); // invalidate all
				sync(TLBEntry);
			}
		}
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		for(int i=0;i<Machine.processor().getTLBSize();i++){
		TranslationEntry entry = Machine.processor().readTLBEntry(i);
		entry.valid=false;
		Machine.processor().writeTLBEntry(i,entry);
		}


		for(TranslationEntry entry:pageTable){
		TranslationEntry e = vmKernel.checkAddress(super.processID(),entry.vpn);
		if(e.valid==false||e==null)
			entry.valid=false;
		else if(e!=null)
			e.valid=true;
	}
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		return super.loadSections();
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		ArrayList<Integer> PPages = new ArrayList<Integer>();
		
	
		for(Map.Entry<PID,TranslationEntry>entry:vmKernel.invertedPageTable.entrySet()){
		if((entry.getKey().pid==super.processID())&&entry.getValue().valid)
			PPages.add(entry.getValue().ppn);
		}
		
		for(Integer i:PPages)
			vmKernel.releasePPage(i);
		

		ArrayList<PID> unMap = new ArrayList<PID>();

		for(PID pid:vmKernel.invertedPageTable.keySet()){
			if(pid.pid==super.processID())
				unMap.add(pid);
		}
		for(PID pid:unMap)
			vmKernel.invertedPageTable.remove(pid);



		
		
	}

	@Override
	protected int handleRead(int fileDescriptor, int vaddrBuffer, int length){
		for(int i=vaddrBuffer/pageSize;i<=(vaddrBuffer+length)/pageSize;i++){
		TranslationEntry entry=vmKernel.checkAddress(super.processID(),i);
		if(entry==null||entry.valid==false)
			handlePageFault(i);
		}

		TranslationEntry e = vmKernel.checkAddress(super.processID(),vaddrBuffer/pageSize);
		vmKernel.pinPage(e.ppn);
		int read = super.handleRead(fileDescriptor, vaddrBuffer, length);
		vmKernel.unpinPage(e.ppn);
		return read;
	}

	@Override
	protected int handleWrite(int a0,int a1, int a2){
	for(int i=a1/pageSize;i<=(a1+a2)/pageSize;i++){
		TranslationEntry entry=vmKernel.checkAddress(super.processID(),i);
		if(entry==null||entry.valid==false)
			handlePageFault(i);
		}

	TranslationEntry e = vmKernel.checkAddress(super.processID(),a1/pageSize);
		vmKernel.pinPage(e.ppn);
		int write = super.handleWrite(a0,a1,a2);
		vmKernel.unpinPage(e.ppn);
		return write;
	}


	@Override
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length){
		for(int i=vaddr/pageSize;i<=(vaddr+length)/pageSize;i++){
		TranslationEntry entry=vmKernel.checkAddress(super.processID(),i);
		entry.used=true;

		if(entry==null||entry.valid==false)
			handlePageFault(i);
		}
		

		return super.readVirtualMemory(vaddr,data,offset,length);

	}

	@Override
		public int readVirtualMemory(int vaddr, byte[] data){
		
		for(int i=vaddr/pageSize;i<=(vaddr+data.length)/pageSize;i++){
		TranslationEntry entry=vmKernel.checkAddress(super.processID(),i);
		entry.used=true;

		if(entry==null||entry.valid==false)
			handlePageFault(i);
		}
		

		return super.readVirtualMemory(vaddr,data);

	}

	@Override
		public String readVirtualMemoryString(int vaddr, int length){
		for(int i=vaddr/pageSize;i<=(vaddr+length)/pageSize;i++){
		TranslationEntry entry=vmKernel.checkAddress(super.processID(),i);
		entry.used=true;

		if(entry==null||entry.valid==false)
			handlePageFault(i);
		}
		

		return super.readVirtualMemoryString(vaddr,length);

	}

	@Override
	public int writeVirtualMemory(int vaddr,byte[] data,int offset,int length){
	for(int i=vaddr/pageSize;i<=(vaddr+length)/pageSize;i++){
		TranslationEntry entry=vmKernel.checkAddress(super.processID(),i);
		entry.used=true;
		entry.dirty=true;

		if(entry==null||entry.valid==false)
			handlePageFault(i);
		}
		
		return super.writeVirtualMemory(vaddr,data,offset,length);
	
	}
	@Override
	public int writeVirtualMemory(int vaddr, byte[] data){
	for(int i=vaddr/pageSize;i<=(vaddr+data.length)/pageSize;i++){
		TranslationEntry entry=vmKernel.checkAddress(super.processID(),i);
		entry.used=true;
		entry.dirty=true;

		if(entry==null||entry.valid==false)
			handlePageFault(i);
		}
		
		return super.writeVirtualMemory(vaddr,data);
	}

	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionTLBMiss:
			handleTLBMiss();
			break;
		default:
			super.handleException(cause);
			break;
		}
	}

	private void handleTLBMiss(){
		Processor processor = Machine.processor();

		int vaddr = processor.readRegister(Processor.regBadVAddr);
		int vpn = Processor.pageFromAddress(vaddr);
		TranslationEntry entry = pageTable[vpn];

		int TLBSize = processor.getTLBSize();
		int i;
		for(i = 0; i < TLBSize; i++){
			TLBEntry = processor.readTLBEntry(i);
			if(!TLBEntry.valid){
				//write to this entry
				processor.writeTLBEntry(i, entry);
				return;
			}
		}
		i = Lib.random(TLBSize);
		//evict page i
		TLBEntry = processor.readTLBEntry(i);
		sync(TLBEntry); //sync with PTE

		processor.writeTLBEntry(i, entry); //overwrite TLB entry
		return;
	}

	private void handlePageFault(int vpn){
	
		if((vpn<0)||(vpn>pageTable.length))
		handleExit(-1);
		
	
		if(vmKernel.inSwap(super.processID(),vpn)){
		
		vmKernel.swapPagesIn(super.processID(),vpn);
		TranslationEntry entry = vmKernel.checkAddress(super.processID(),vpn);
		pageTable[vpn] = entry;
		return;
		}

		CoffSection sec=null;
		boolean nonExec=false;
		int i=0;
		for(int j=0;j<coff.getNumSections()&&!nonExec;j++){
		sec = coff.getSection(j);
			for(i=0;i<sec.getLength();i++){
			if(sec.getFirstVPN()+i==vpn)
				nonExec = true;
			break;
			}
		}

		TranslationEntry newEntry=vmKernel.findFreePage(super.processID(),vpn,(!sec.isReadOnly()||!nonExec),(sec.isReadOnly()&&sec!=null));
		pageTable[vpn] = newEntry;

		if(nonExec){
		sec.loadPage(i,newEntry.ppn);
		return;
		
		}
	
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	VMKernel vmKernel;

	private static final int pageSize = Processor.pageSize;
	
	protected TranslationEntry TLBEntry;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
