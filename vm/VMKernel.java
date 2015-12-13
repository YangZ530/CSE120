package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.util.*;
/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {

	public class PID{
		public int vpn;
		public int pid;
		
		public PID(int pid, int vpn)
		{
		this.pid=pid;
		this.vpn=vpn;
		}

		public int hashCode(){
			return new String(pid+"#"+vpn).hashCode();	
		}

			
	}
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
		freePages = new LinkedList<Integer>();
		for(int i=0; i<64;i++)
			freePages.add(i);

		pinnedPages = new ArrayList<Integer>();
		unpinnedPages = new ArrayList<TranslationEntry>();
		invertedPageTable = new HashMap<PID, TranslationEntry>();
		swappedPages = new HashMap<PID, TranslationEntry>();
		pageLocation = new HashMap<PID, Integer>();

		freePages = new LinkedList<Integer>();
		for(int i=0; i<64;i++)
			freePages.add(i);
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);

		swapFile = ThreadedKernel.fileSystem.open("vm.swp",true);
		pinPageLock = new Lock();
		PTLock = new Lock();
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
		swapFile.close();
		ThreadedKernel.fileSystem.remove(swapFile.getName());
	}

	public void pinPage(Integer pid){
	pinPageLock.acquire();
	pinnedPages.add(pid);
	pinPageLock.release();
	}

	public void unpinPage(Integer pid){
	pinPageLock.acquire();
	pinnedPages.remove(pid);
	pinPageLock.release();
	}

	public int swapPagesIn(int pid, int vpn){
	Integer loc = pageLocation.get(new PID(pid, vpn));

	
	int pageSize = Processor.pageSize;
	byte[] buff = new byte[pageSize];
	swapFile.seek(loc*pageSize);
	int dataRead = swapFile.read(buff, 0, pageSize);

	

	if(dataRead<Processor.pageSize)
		terminate();

	TranslationEntry freepage = findFreePage(pid,vpn,true,false);
	byte[] memory = Machine.processor().getMemory();
	
	System.arraycopy(buff, 0, memory, freepage.ppn*pageSize,pageSize);

	if(unpinnedPages.contains(freepage))
		unpinnedPages.add(freepage);

	
	freePages.add(loc);
	pageLocation.remove(new PID(pid,vpn));
	swappedPages.remove(new PID(pid,vpn));

	return freepage.ppn;
	}

	private int swapPagesOut(){
	
	PTLock.acquire();
	pinPageLock.acquire();
	TranslationEntry entry = null;
	int i;
	for(i=0; i<unpinnedPages.size()*2;i++){
		TranslationEntry e = unpinnedPages.get(i%unpinnedPages.size());
		if(pinnedPages.contains(e.ppn)){
			continue;}
		if(entry.used){
			entry = e;
			break;}
		else
			e.used=false;

	}
	unpinnedPages.remove(i%unpinnedPages.size());
	PID key = null;
	for(Map.Entry<PID,TranslationEntry> e : invertedPageTable.entrySet()){
	if((e.getValue().vpn == entry.vpn)&&(e.getValue().ppn==entry.ppn))
	{
		key = e.getKey();
		break;
		}
	}

	invertedPageTable.remove(key);
	byte[] memory = Machine.processor().getMemory();
	byte[] swappage = new byte[pageSize];
	
	System.arraycopy(memory,entry.ppn*pageSize,swappage,0,pageSize );
	int swapLoc = freePages.removeFirst();
	swapFile.seek(swapLoc*pageSize);
	int write = swapFile.write(swappage,0,pageSize);
	if(write!=pageSize){
	terminate();
	}
	swappedPages.put(key,entry);
	pageLocation.put(key,swapLoc);

	Arrays.fill(swappage,(byte)0);
	System.arraycopy(swappage,0,memory,entry.ppn*pageSize,pageSize);


	PTLock.release();
	pinPageLock.release();
	return entry.ppn;
	}

	public TranslationEntry checkAddress(int pid, int vpn){
		TranslationEntry entry = invertedPageTable.get(new PID(pid, vpn));
		return entry;
	}
 
	public TranslationEntry findFreePage(int id, int vpn, boolean unpinned, boolean readOnly){
	
		
		int freePageNum;
		if(super.freePagesNum()>0)
			freePageNum = super.findFreePages();
		else
			freePageNum = swapPagesOut();

		TranslationEntry newFreePage = new TranslationEntry(vpn,freePageNum,true,readOnly,false,false);
		if(unpinned=true)
			unpinnedPages.add(newFreePage);
		PTLock.acquire();
		invertedPageTable.put(new PID(id,vpn),newFreePage);
		PTLock.release();
		return newFreePage;
	}

	public void releasePPage(int ppn){
	PPLock.acquire();
	if(freePPages.contains(ppn)){
		PPLock.release();
		return;
	}
	freePPages.add(ppn);
	PID unMap = null;
	for(Map.Entry<PID,TranslationEntry> entry:invertedPageTable.entrySet())
	{
	if(entry.getValue().ppn==ppn){
		unMap = entry.getKey();
		if(unpinnedPages.contains(entry.getValue()))
			unpinnedPages.remove(entry.getValue());
		break;
	}
	}
	if(unMap!=null)
		invertedPageTable.remove(unMap);
	PPLock.release();

	}
	
	public boolean inSwap(int pid, int vpn){
		return swappedPages.containsKey(new PID(pid,vpn));}


	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';


	protected Lock pinPageLock;
	protected Lock PTLock;
	protected Lock PPLock;
	
	//a globle inverted page table
	public HashMap<PID, TranslationEntry> invertedPageTable;

	//avaliable free swap pages
	protected LinkedList<Integer> freePages;
	
	//pages that should not be swapped out
	protected ArrayList<Integer> pinnedPages;

	//pages that can be swapped out
	protected ArrayList<TranslationEntry> unpinnedPages;

	//pages that swapped out to disk
	protected HashMap<PID, TranslationEntry> swappedPages;

	//the location of a specific page in swap
	protected HashMap<PID, Integer> pageLocation;

	protected OpenFile swapFile;

	private final int pageSize = Processor.pageSize;



}
