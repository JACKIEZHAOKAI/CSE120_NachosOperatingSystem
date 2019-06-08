zhx121_chl749_r1chu

###########################################################
As a final step, create a file named README in the proj3 directory. The README file should list the members of your group and provide a short description of what code you wrote, how well it worked, how you tested your code, and how each group member contributed to the project. The goal is to make it easier for us to understand what you did as we grade your project in case there is a problem with your code, not to burden you with a lot more work. Do not agonize over wording. It does not have to be poetic, but it should be informative.

###########################################################

Group members: 

ZHAOKAI XU: implemented the handlePageFaultException() to be called when a page called in readVM and writeVM is invalid, lock before calling and unlock after calling handlePageFaultException() since we are accesiing VMKernel shared variables in this func.

handlePageFaultException() takes a badVirtualAddr and check if the badVirtualAddr is a fault on a code page OR a fault on a stack/arguments page, and decide to read the corresponding code/ data page from the COFF file OR zero-all the frame.

for the 1st case, iterate all the pages in all sections, find the vpn, assign ppn by allocating one phy page from phy memory.
if the phy mem is not full, we just take one from the freePhyPages, and init the entry of the invertedPageTable with the pageTable entry.
Otherwise, run the LRUclock() algorithm to find a page to be evicted in phy mem. 
if the ppn of the old page to be evicted is dirty/modified, we want to write/swap out the old page into the swapfile, and store the swapPageNum in pageTable entry of curr process so that the next time it can be found, meanwhile.

Next, we want to update the invertedPageTable with new page's pageTable entry, 
check if the pageTable entry of the new page is dirty, if not, just load page into phy mem, Otherwise, read/swap in page from swapfile to access the most recent page.
finally set VMKernel.invertedPageTable[ppn].entry = pageTable[vpn];

For the 2nd case where the fault page in stack and arg, we handle it similarly, the only difference is instead of calling section.loadPage(i, ppn) to load a page from this segment into physical memory, we zero-fill the frame.


pin and unpin before and after arraycopy in readVirtualMemory writeVirtualMemory.
handle it using sychronization, sleep current process only if all pages in phy mem are pinned, wake the process when some process's arraycopy is finished.


CHENGJUN LU


Ryan Chu:  
