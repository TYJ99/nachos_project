Group Member: Yen-Ju Tseng(A59005785)    
## Implementation  
### 1. Demand Paging: Lazy Loading  
  * Demand paging: Pages are allocated on a need-only basis.  
    - Initially, there are no pages in memory  
    - As process runs, keep loading pages into (physical) memory as they are referenced  
      - If (physical) memory is full, evict pages!  
  * Lazy loading: Initially (i.e. at process creation time), NO page is loaded.  
    - Even the first instruction the process runs must be loaded using demand paging.  
  * Initialize PPN with -1 and valid bit to 0(false).  

### 2. Page Fault  
  * If the page is in swap file, no need to check if it is a coff page or a stack page.
    - Load from swap file
  * If the page is not in swap file
    - If it is a **stack or arg page**, zero-fill it.
    - If it is a **coff page**, load it from coff.  

### 3. Page Replacement  
  * If **ALL** pages in physical memory are pinned, a new page cannot be brought into the memory  
    and the corresponding process will have to wait(put it to sleep) until another process unpins a page.  
  * Choose a victim page to be replaced by using "Clock Algorithm" aka "second chance page replacement algorithm".  
    - Pick a page which is not used recently and is **NOT Pinned**.
  * Eviction:  
    - If the victim page is dirty, swap it out to the swap file.
      - Swap out: 
        - If this page is swaped out before(already has swap page number), swap it out to the same spn.  
        Otherwise, assign a new spn to store this victim page.
    - Set ppn to -1, valid bit to 0(false), and used bit to 0(false).   
  
### 4. Page Pinning for Multiple Processes
  * If a process encounter a context switch when it is accessing the physical page(like read/write),  
    the process have to pin that physical page so that it won't lose the data.
  * A process needs to “pin” its physical pages in memory. Such pages cannot be swapped out until they are unpinned by the owner process.  
  * Implement it in writeVirtualMemory and readVirtualMemory.
  * If **ALL** pages in physical memory are pinned, a new page cannot be brought into the memory  
    and the corresponding process will have to wait(put it to sleep) until another process unpins a page.(I implemented it in page replacement)  
   
### 5. Synchronization for Multiple Processes
  * Add two locks: pinLock and pFExceptionLock  
  * Add one condition variable: fullPinCV with pFExceptionLock    
  * Lock the process with pinLock when it is pinning the page during read/write virtual memory.  
  * Lock handlePageFaultException with pFExceptionLock. If page fault exception is not triggered by read/write virtual memory,  
    also lock it with pinLock so that other processes cannot evict the physical page which is being written or read.  

### Note
1. Reuse(Free) swap file pages only when the process terminates. Once the modified page has been swapped out
   to the swap file, always bring it back to physical memory from swap file even it is not modified on the next few page faults.
2. Make sure you always access the **latest** physical memory when you try to do some operations on physical memory since you might  
   encounter a context switch after you access physical. When it return back again, you won't get the updated data in physical memory.
3. For used bit: Used bit:  
   you want to set the used bit to false when you load a page from the COFF or swap files,  
   or when initializing a stack/arg page for the first time. In other words, as a result of handling a page fault on   
   virtual page P, the page fault handler should set the used bit to false for P.
4. For dirty bit:  
   We need to manually set the dirty bit when we write (wVM) since the Processor won't handle that for us(since the page table is created by the VMProcess.)
   


