package simpledb;


import org.jgrapht.DirectedGraph;

import java.io.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BufferPool manages the reading and writing of pages into memory from
 * disk. Access methods call into it to retrieve pages, and it fetches
 * pages from the appropriate location.
 * <p>
 * The BufferPool is also responsible for locking;  when a transaction fetches
 * a page, BufferPool checks that the transaction has the appropriate
 * locks to read/write the page.
 *
 * @Threadsafe, all fields are final
 */
public class BufferPool {
    /** Bytes per page, including header. */
    public static final int PAGE_SIZE = 4096;

    /** Default number of pages passed to the constructor. This is used by
     other classes. BufferPool should use the numPages argument to the
     constructor instead. */
    public static final int DEFAULT_PAGES = 50;

    final int numPages;   // number of pages
    final ConcurrentHashMap<PageId,Page> pages; // hash table storing current pages in memory
    private final Random random = new Random(); // for choosing random pages for eviction

    /** TODO for Lab 4: create your private Lock Manager class.
     Be sure to instantiate it in the constructor. */
    private final LockManager lockmgr; // Added for Lab 4

    /**
     * Creates a BufferPool that caches up to numPages pages.
     *
     * @param numPages maximum number of pages in this buffer pool.
     */
    public BufferPool(int numPages) {
        // some code goes here
        this.numPages = numPages;
        this.pages = new ConcurrentHashMap<PageId, Page>();

        lockmgr = new LockManager(); // Added for Lab 4
    }

    public static int getPageSize() {
        return PAGE_SIZE;
    }

    /**
     * Retrieve the specified page with the associated permissions.
     * Will acquire a lock and may block if that lock is held by another
     * transaction.
     * <p>
     * The retrieved page should be looked up in the buffer pool.  If it
     * is present, it should be returned.  If it is not present, it should
     * be added to the buffer pool and returned.  If there is insufficient
     * space in the buffer pool, an page should be evicted and the new page
     * should be added in its place.
     *
     * @param tid the ID of the transaction requesting the page
     * @param pid the ID of the requested page
     * @param perm the requested permissions on the page
     */
    public Page getPage(TransactionId tid, PageId pid, Permissions perm)
            throws TransactionAbortedException, DbException {

        // Added for Lab 4: acquire the lock on the page first
        try {
            lockmgr.acquireLock(tid, pid, perm);
        } catch (DeadlockException e) {
            throw new TransactionAbortedException(); // caught by callee, who calls transactionComplete()
        }


        Page p;
        synchronized(this) {
            p = pages.get(pid);
            if(p == null) {
                if(pages.size() >= numPages) {

                    evictPage(); // Added for lab 2
                }

                p = Database.getCatalog().getDatabaseFile(pid.getTableId()).readPage(pid);
                pages.put(pid, p);
            }
        }
        return p;
    }

    /**
     * Releases the lock on a page.
     * Calling this is very risky, and may result in wrong behavior. Think hard
     * about who needs to call this and why, and why they can run the risk of
     * calling it.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param pid the ID of the page to unlock
     */
    public  void releasePage(TransactionId tid, PageId pid) {
        lockmgr.releaseLock(tid,pid); // Added for Lab 4
    }

    /**
     * Release all locks associated with a given transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     */
    public void transactionComplete(TransactionId tid) throws IOException {
        transactionComplete(tid,true); // Added for Lab 4
    }

    /** Return true if the specified transaction has a lock on the specified page */
    public boolean holdsLock(TransactionId tid, PageId p) {
        return lockmgr.holdsLock(tid, p); // Added for Lab 4
    }

    /**
     * Commit or abort a given transaction; release all locks associated to
     * the transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param commit a flag indicating whether we should commit or abort
     */
    public void transactionComplete(TransactionId tid, boolean commit)
            throws IOException {
        Page p;
        if (commit){
            for (PageId pid: pages.keySet()){
                p = pages.get(pid);
                if (p.isDirty()!=null && p.isDirty() ==tid){
                    p.setBeforeImage();
                    flushPage(pid);
                }
            }
        }
        else{
            for (PageId pid: pages.keySet()){
                p = pages.get(pid);
                if (p.isDirty()!=null && p.isDirty() ==tid){
                    pages.put(pid,p.getBeforeImage());
                }
            }
        }

        lockmgr.releaseAllLocks(tid, commit); // Added for Lab 4
    }

    /**
     * Add a tuple to the specified table on behalf of transaction tid.  Will
     * acquire a write lock on the page the tuple is added to and any other
     * pages that are updated (Lock acquisition is not needed for lab2).
     * May block if the lock(s) cannot be acquired.
     *
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and updates cached versions of any pages that have
     * been dirtied so that future requests see up-to-date pages.
     *
     * @param tid the transaction adding the tuple
     * @param tableId the table to add the tuple to
     * @param t the tuple to add
     */
    public void insertTuple(TransactionId tid, int tableId, Tuple t)
            throws DbException, IOException, TransactionAbortedException {

        DbFile file = Database.getCatalog().getDatabaseFile(tableId);

        // let the specific implementation of the file decide which page to add it to
        ArrayList<Page> dirtypages = file.insertTuple(tid, t);

        synchronized(this) {
            for (Page p : dirtypages){
                p.markDirty(true, tid);

                // if page in pool already, done.
                if(pages.get(p.getId()) != null) {
                    //replace old page with new one in case insertTuple returns a new copy of the page
                    pages.put(p.getId(), p);
                }
                else {

                    // put page in pool
                    if(pages.size() >= numPages)
                        evictPage();
                    pages.put(p.getId(), p);
                }
            }
        }
    }

    /**
     * Remove the specified tuple from the buffer pool.
     * Will acquire a write lock on the page the tuple is removed from and any
     * other pages that are updated. May block if the lock(s) cannot be acquired.
     *
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and updates cached versions of any pages that have
     * been dirtied so that future requests see up-to-date pages.
     *
     * @param tid the transaction deleting the tuple.
     * @param t the tuple to delete
     */
    public  void deleteTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {

        DbFile file = Database.getCatalog().getDatabaseFile(t.getRecordId().getPageId().getTableId());
        ArrayList<Page> dirtypages = file.deleteTuple(tid, t);

        synchronized(this) {
            for (Page p : dirtypages){
                p.markDirty(true, tid);
            }
        }
    }

    /**
     * Flush all dirty pages to disk.
     * Be careful using this routine -- it writes dirty data to disk so will
     *     break simpledb if running in NO STEAL mode.
     */
    public synchronized void flushAllPages() throws IOException {

        Iterator<PageId> i = pages.keySet().iterator();
        while(i.hasNext())
            flushPage(i.next());

    }

    /** Remove the specific page id from the buffer pool.
     Needed by the recovery manager to ensure that the
     buffer pool doesn't keep a rolled back page in its
     cache.
     */
    public synchronized void discardPage(PageId pid) {
        // some code goes here
        // not necessary for labs 1--4
    }

    /**
     * Flushes a certain page to disk
     * @param pid an ID indicating the page to flush
     */
    private synchronized void flushPage(PageId pid) throws IOException {

        Page p = pages.get(pid);
        if (p == null)
            return; //not in buffer pool -- doesn't need to be flushed

        DbFile file = Database.getCatalog().getDatabaseFile(pid.getTableId());
        file.writePage(p);
        p.markDirty(false, null);
    }

    /** Write all pages of the specified transaction to disk.
     */
    public synchronized void flushPages(TransactionId tid) throws IOException {
        // some code goes here
        // not necessary for labs 1--4
    }

    /**
     * Discards a page from the buffer pool.
     * Flushes the page to disk to ensure dirty pages are updated on disk.
     * Throws a DbException if unable to evict a page
     */
    private synchronized void evictPage() throws DbException {

        // try to evict a random page, focusing first on finding one that is not dirty
        Object pids[] = pages.keySet().toArray();


        ArrayList<PageId> cleanPages = new ArrayList<PageId>();
        for(PageId p : pages.keySet()){
            if (pages.get(p).isDirty() == null){
                cleanPages.add(p);
            }
        }
        if (cleanPages.isEmpty()){
            throw new DbException("No clean pages to evict");
        }
        PageId pid = (PageId) cleanPages.get(random.nextInt(cleanPages.size()));

        try {
            flushPage(pid); // flush whichever one we ended up with, which may have been a dirty one
        } catch (IOException e) {
            throw new DbException("could not evict page");
        }
        pages.remove(pid);
    }

    /**
     * Manages locks on PageIds held by TransactionIds.
     * S-locks and X-locks are represented as Permissions.READ_ONLY and Permisions.READ_WRITE, respectively
     *
     * All the field read/write operations are protected by this
     * @Threadsafe
     */
    private class LockManager {

        final int LOCK_WAIT = 10;       // ms
        private static final int TIMEOUT = 500;
        private ConcurrentHashMap<TransactionId,HashSet<PageId>> trans2PageMap;
        private ConcurrentHashMap<PageId,Permissions> lockMap;
        private ConcurrentHashMap<PageId, HashSet<TransactionId>> page2TransMap;
        private ConcurrentHashMap<TransactionId,Integer> waitMap;
        private DirectedGraph waitGraph;

        /**
         * Sets up the lock manager to keep track of page-level locks for transactions
         * Should initialize state required for the lock table data structure(s)
         */
        private LockManager() {
            trans2PageMap = new ConcurrentHashMap<TransactionId, HashSet<PageId>>();
            lockMap = new ConcurrentHashMap<PageId, Permissions>();
            page2TransMap = new ConcurrentHashMap<PageId, HashSet<TransactionId>>();

        }


        /**
         * Tries to acquire a lock on page pid for transaction tid, with permissions perm.
         * If cannot acquire the lock, waits for a timeout period, then tries again.
         *
         * In Exercise 5, checking for deadlock will be added in this method
         * Note that a transaction should throw a DeadlockException in this method to
         * signal that it should be aborted.
         *
         * @throws DeadlockException after on cycle-based deadlock
         */
        @SuppressWarnings("unchecked")
        public boolean acquireLock(TransactionId tid, PageId pid, Permissions perm)
                throws DeadlockException {
            int waitTime = 0;
            while(!lock(tid, pid, perm)) { // keep trying to get the lock

                synchronized(this) {
                    waitTime +=LOCK_WAIT;
                    if (waitTime >= TIMEOUT){
                        releaseAllLocks(tid,false);
                        throw new DeadlockException();
                    }
                }

                try {
                    Thread.sleep(LOCK_WAIT); // couldn't get lock, wait for some time, then try again
                } catch (InterruptedException e) {
                }

            }

            synchronized(this) {
                // for Exercise 5, might need some cleanup on deadlock detection data structure
            }

            return true;
        }


        /**
         * Release all locks corresponding to TransactionId tid.
         * Check lab description to make sure you clean up appropriately depending on whether transaction commits or aborts
         */
        public synchronized void releaseAllLocks(TransactionId tid, boolean commit) {

            if (trans2PageMap.get(tid)==null){
                return;
            }
            for (PageId pid: trans2PageMap.get(tid)){
                HashSet<TransactionId> ts = page2TransMap.get(pid);
                if (ts.size()==1){
                    page2TransMap.remove(pid);
                    lockMap.remove(pid);
                }
                else{
                    ts.remove(pid);
                    page2TransMap.put(pid,ts);
                }
            }
            trans2PageMap.remove(tid);
        }



        /** Return true if the specified transaction has a lock on the specified page */
        public synchronized boolean holdsLock(TransactionId tid, PageId p) {
            if (lockMap.containsKey(p)){
                return page2TransMap.get(p).contains(tid);
            }
            return false;
        }

        /**
         * Answers the question: is this transaction "locked out" of acquiring lock on this page with this perm?
         * Returns false if this tid/pid/perm lock combo can be achieved (i.e.., not locked out), true otherwise.
         *
         * Logic:
         *
         * if perm == READ
         *  if tid is holding any sort of lock on pid, then the tid can acquire the lock (return false).
         *
         *  if another tid is holding a READ lock on pid, then the tid can acquire the lock (return false).
         *  if another tid is holding a WRITE lock on pid, then tid can not currently
         *  acquire the lock (return true).
         *
         * else
         *   if tid is THE ONLY ONE holding a READ lock on pid, then tid can acquire the lock (return false).
         *   if tid is holding a WRITE lock on pid, then the tid already has the lock (return false).
         *
         *   if another tid is holding any sort of lock on pid, then the tid can not currenty acquire the lock (return true).
         */
        private synchronized boolean locked(TransactionId tid, PageId pid, Permissions perm) {
            Permissions pagePerm = lockMap.get(pid);

            if (pagePerm == null){
                return false;
            }

            if (holdsLock(tid,pid) && lockMap.get(pid) == perm){
                return false;
            }

            //if we are trying to get a slock
            if (perm == Permissions.READ_ONLY){
                //if tid has a lock on pid already
                if (holdsLock(tid,pid)){
                    return false;
                }
                //if the page is slocked
                else if (pagePerm == Permissions.READ_ONLY){
                    return false;
                }
                //if the page is xlocked
                //HERE IS THE ISSUE
                else if (!holdsLock(tid,pid) &&pagePerm == Permissions.READ_WRITE){
                    return true;
                }
            }

            //If we are trying to get a xlock
            else{
                //If the tid has a lock
                if (holdsLock(tid,pid)){

                    //if this tid is the only thing locking pid
                    if (page2TransMap.get(pid).size()==1){
                        return false;
                    }
                    else{
                        return true;
                    }
                }


            }
            return true;
        }



        /*
         * Releases whatever lock this transaction has on this page
         * Should update lock table
         *
         * Note that you do not need to "wake up" another transaction that is waiting for a lock on this page,
         * since that transaction will be "sleeping" and will wake up and check if the page is available on its own
         * If you decide to change the fact that a thread is sleeping in acquireLock(), you would have to wake it up here
         */
        public synchronized void releaseLock(TransactionId tid, PageId pid) {
            HashSet<PageId> ps = trans2PageMap.get(tid);
            if (ps.size()==1){
                trans2PageMap.remove(tid);
            }
            else{
                ps.remove(pid);
                trans2PageMap.put(tid,ps);
            }

            HashSet<TransactionId> ts = page2TransMap.get(pid);
            if (ts.size()==1){
                page2TransMap.remove(pid);
                lockMap.remove(pid);
            }
            else{
                ts.remove(pid);
                page2TransMap.put(pid,ts);
            }
        }


        /*
         * Attempt to lock the given PageId with the given Permissions for this TransactionId
         * Should update the lock table
         *
         * Returns true if the attempt was successful
         */
        private synchronized boolean lock(TransactionId tid, PageId pid, Permissions perm) {

            Permissions lockType = lockMap.get(pid);
            if(locked(tid, pid, perm)) {
                return false; // this transaction cannot get the lock on this page; it is "locked out"
            }

            //If this tid already has a lock
            if (holdsLock(tid,pid)){
                //if we already have the correct lock
                if (lockType==perm){
                    return true;
                }
                //if we want to upgrade to xlock
                else if(perm == Permissions.READ_WRITE){
                    if (page2TransMap.get(pid).size() ==1){
                        lockMap.put(pid,Permissions.READ_WRITE);
                        return true;
                    }
                }

                return true;
            }

            // Add to the Lockmap if needed
            if (lockType == null){
                lockMap.put(pid,perm);
            }

            //Add to the p2t map
            HashSet<TransactionId> ts = page2TransMap.get(pid);
            if (ts==null){
                ts = new HashSet<TransactionId>();
            }
            ts.add(tid);
            page2TransMap.put(pid, ts);

            HashSet<PageId> ps = trans2PageMap.get(tid);
            if (ps == null){
                ps = new HashSet<PageId>();
            }
            ps.add(pid);
            trans2PageMap.put(tid, ps);

            return true;
        }
    }

}
