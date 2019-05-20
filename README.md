# NachosOperatingSystem

### PA2 PA2  multiProgramming support under UserProg/


########################################################### As a final step, create a file named README in the proj2 directory. The README file should list the members of your group and provide a short description of what code you wrote, how well it worked, how you tested your code, and how each group member contributed to the project. The goal is to make it easier for us to understand what you did as we grade your project in case there is a problem with your code, not to burden you with a lot more work. Do not agonize over wording. It does not have to be poetic, but it should be informative. ###########################################################

Group members:

ZHAOKAI XU I implemented the file system calls in part1 except for read() and write(); readVirtualMemory() and writeVirtualMemory() to support multiprogramming by allowing multiple user processes sharing memory; handleExec(), handleJoin(), handleExit() and even handleHalt in part3 to support multiprogramming by allowing multi-process interacting with each others(ex, child and parent relationship in handleJoin() )

UserProcess() allocate a fileTable of OpenFile obj, assign PID for each process (where the PID generator is in kernel); allocate a childProcessMap to map each childPID to child Process, a reference to its parent process; childStatusMap to keep track of the childStatus when child exit; a cvLock and a cv to coordinate the child and parent process in handleJoin(); a isNormal boolean var to indicate if child process exit abnormally and an abnormalChildSet to store the child PID.

In part 1 For handleCreate() and handleOpen(), we implement handleFileDescriptor() to assign a file descriptor for each OpenFile object, it check all the edge cases and finally return a file descriptor, handleCreate will open a file if it exists and create a new file if not.

handleClose() takes a file descriptor, check edge case and close the OpenFile obj,

handleUnlink() takes a virtual address, check edge case and remove the file.

In part2, both readVirtualMemory() and writeVirtualMemory try to resolve the phy addr given a virtual addr of a user process, write or read data in phy memory into the localBuffer into virtual memory in kernel. Note that both methods handle the case when we are writing or reading across a virtual page, we need to find the two mapping phy pages and write or read data from each separately.

In part3,

handleExec() resolve the filename and then add all argument into a String list.. Then, create a child process by calling childProcess(), and excute by calling execute(filename, argvList). Then we save the details of child in parent and details of parent in child and return the PID.

handleExit() deallocate all the resources related to current process, if it has a parent and current process exit normally, we add its PID and status into its parent’s childStatusMap, and wake up its parent. finally, terminate the thread if it is the last process, and call KThread.finish().

I implement handleJoin() using cv and lock, firstly check if a child process exit normally OR abnormally, then call cv.sleep() to let current process wait until child process is completed, finally when child is completed, we check again if a child process exit normally.

handleHalt() just ensure that only the root process can call halt().

All the comments I put on the implementation of functions should be sufficient in explaining how it works and why we implement in this way.

CHENGJUN LU

I implement the system calls handleRead and handleWrite in part 1 and do pair programming of readVirtualMemory in part 2 and all other system call in part 3. We pass the all given test case.

handleRead(): Key idea : we decided to use pageSize(1024 bytes) as the maxsize we can read for each iteration. This help reduces workload when we handle readVirtualMemory and writeVituralMemory.

There are mainly 3 case: Read greater than 1024 bytes Read equal to 1024 bytes Read Less than 1024 bytes We combine case one and case two to make the code more compact. If request read is more than 1024 bytes, we just read 1024 bytes for the current iteration and read the rest for the next time. There are some edges cases that we could not read enough number of bytes as we request. We return -1 for such cases.

handelWrite(): Similar to Read and easier cause we don’t need to take care the case that read() never waits for a stream to have more data, it always returns as much as possible immediately.

ReadVirtual Memory: We only need to take care of two cases since the maxLength we pass is 1024 bytes. The thing we read is inside the page The thing we read is cross 2 different page See more detail in the code comment

RYAN CHU I helped provide ideas and went over the logic of the code that my teammates wrote. I also wrote and ran tests and checked the code to make sure that the written methods will work properly. For the syscalls of part 1, we tested the methods by reopening and closing multiple files, and writing and reading for each. We also made sure that the file descriptors for every file was valid and operational.

We all work happily and contribute in completing the project together. We share ideas and thoughts.
