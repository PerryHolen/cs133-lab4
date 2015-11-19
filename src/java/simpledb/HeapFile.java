package simpledb;

import java.io.*;
import java.nio.Buffer;
import java.util.*;

/**
 * HeapFile is an implementation of a DbFile that stores a collection of tuples
 * in no particular order. Tuples are stored on pages, each of which is a fixed
 * size, and the file is simply a collection of those pages. HeapFile works
 * closely with HeapPage. The format of HeapPages is described in the HeapPage
 * constructor.
 * 
 * @see simpledb.HeapPage#HeapPage
 * @author Sam Madden
 */
public class HeapFile implements DbFile {

    File file;
    TupleDesc tupleDesc;
    int maxPageNo;
    /**
     * Constructs a heap file backed by the specified file.
     * 
     * @param f
     *            the file that stores the on-disk backing store for this heap
     *            file.
     */
    public HeapFile(File f, TupleDesc td) {
        file = f;
        tupleDesc = td;
        this.maxPageNo = (int) f.length() / BufferPool.getPageSize() - 1;
    }

    /**
     * Returns the File backing this HeapFile on disk.
     * 
     * @return the File backing this HeapFile on disk.
     */
    public File getFile() {
        return file;
    }

    /**
     * Returns an ID uniquely identifying this HeapFile. Implementation note:
     * you will need to generate this tableid somewhere ensure that each
     * HeapFile has a "unique id," and that you always return the same value for
     * a particular HeapFile. We suggest hashing the absolute file name of the
     * file underlying the heapfile, i.e. f.getAbsoluteFile().hashCode().
     * 
     * @return an ID uniquely identifying this HeapFile.
     */
    public int getId() {
        return file.getAbsoluteFile().hashCode();
    }

    /**
     * Returns the TupleDesc of the table stored in this DbFile.
     * 
     * @return TupleDesc of this DbFile.
     */
    public TupleDesc getTupleDesc() {
        return tupleDesc;
    }

    // see DbFile.java for javadocs
    public Page readPage(PageId pid) {
        try {
            RandomAccessFile read = new RandomAccessFile(file,"r");
            int offset = BufferPool.PAGE_SIZE * pid.pageNumber();
            byte[] content = new byte[BufferPool.PAGE_SIZE];
            read.seek(offset);
            read.read(content,0, BufferPool.getPageSize());
            read.close();
            return new HeapPage((HeapPageId)pid,content);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    // see DbFile.java for javadocs
    public void writePage(Page page) throws IOException {

        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        int offset = BufferPool.PAGE_SIZE * page.getId().pageNumber();
        raf.seek(offset);
        raf.write(page.getPageData(), 0, BufferPool.PAGE_SIZE);
        raf.close();

    }

    public HeapPage getOpenPage(TransactionId tid) throws TransactionAbortedException, DbException {
        PageId pid;
        HeapPage hPage;
        for (int i = 0; i < numPages(); i++) {
            pid = new HeapPageId(getId(),i);
            hPage = (HeapPage) Database.getBufferPool().getPage(tid,pid,Permissions.READ_WRITE);
            if (hPage.getNumEmptySlots()>0){
                return hPage;
            }
        }
        return null;
    }

    /**
     * Returns the number of pages in this HeapFile.
     */
    public int numPages() {
        return (int) Math.ceil(this.file.length()/ BufferPool.PAGE_SIZE);
    }




    // see DbFile.java for javadocs
    public ArrayList<Page> insertTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        HeapPage insertPage = getOpenPage(tid);
        if (insertPage!= null){
            insertPage.insertTuple(t);
        }
        else{
            HeapPageId id = new HeapPageId(getId(),numPages());
            insertPage = new HeapPage(id,HeapPage.createEmptyPageData());
            insertPage.insertTuple(t);
            writePage(insertPage);
        }
        return new ArrayList<Page>(Arrays.asList(insertPage));
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> deleteTuple(TransactionId tid, Tuple t) throws DbException,
            TransactionAbortedException {
        PageId pid = t.getRecordId().getPageId();
        HeapPage deletePage = (HeapPage) Database.getBufferPool().getPage(tid,pid,Permissions.READ_WRITE);
        deletePage.deleteTuple(t);
        return new ArrayList<Page>(Arrays.asList(deletePage));
    }

    // see DbFile.java for javadocs
    public DbFileIterator iterator(TransactionId tid) {
        return new DbFileIterator() {

            // Variables to keep the state of the iterator.
            private int curPage = 0;
            private Page page = null;
            private PageId pID = null;
            private Iterator<Tuple> tupleIt = null;
            private TransactionId tID = null;
            private HeapFile hFile;

            // Method for setting the transaction id of the iterator.
            private DbFileIterator setTid(TransactionId tid) {
                tID = tid;
                return this;
            }

            @Override
            public void open() throws DbException, TransactionAbortedException {
                // Initialize the state.
                int tableId = HeapFile.this.getId();
                pID = new HeapPageId(tableId, curPage);
                page = Database.getBufferPool().getPage(tID, pID, null);
                tupleIt = ((HeapPage) page).iterator();
            }

            @Override
            public boolean hasNext() throws DbException,
                    TransactionAbortedException {
                if (tupleIt != null) {
                    if (tupleIt.hasNext()) {
                        return true;
                    } else {
                        if (curPage < HeapFile.this.maxPageNo) {
                            int tableId = HeapFile.this.getId();
                            curPage++;
                            pID = new HeapPageId(tableId, curPage);
                            page = Database.getBufferPool().getPage(tID, pID, null);
                            tupleIt = ((HeapPage) page).iterator();
                            return hasNext();
                        }
                    }
                }
                return false;
            }

            @Override
            public Tuple next() throws DbException,
                    TransactionAbortedException, NoSuchElementException {
                if (tupleIt != null) {
                    return tupleIt.next();
                } else {
                    throw new NoSuchElementException();
                }
            }

            @Override
            public void rewind() throws DbException,TransactionAbortedException {
                close();
                open();
            }

            @Override
            public void close() {
                curPage = 0;
                pID = null;
                page = null;
                tupleIt = null;

            }

        }.setTid(tid);
    }

}

