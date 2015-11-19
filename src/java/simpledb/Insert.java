package simpledb;

import javax.xml.crypto.Data;
import java.io.IOException;

/**
 * Inserts tuples read from the child operator into the tableid specified in the
 * constructor
 */
public class Insert extends Operator {

    private static final long serialVersionUID = 1L;

    private TransactionId tid;
    private DbIterator childIt;
    private int tableId;
    private boolean inserted;
    private TupleDesc desc;

    /**
     * Constructor.
     * 
     * @param t
     *            The transaction running the insert.
     * @param child
     *            The child operator from which to read tuples to be inserted.
     * @param tableid
     *            The table in which to insert tuples.
     * @throws DbException
     *             if TupleDesc of child differs from table into which we are to
     *             insert.
     */
    public Insert(TransactionId t,DbIterator child, int tableid)
            throws DbException {
        tid = t;
        childIt = child;
        tableId = tableid;
        inserted = false;

        String[] names = new String[]{"Inserted"};
        Type[] types = new Type[] {Type.INT_TYPE};
        desc = new TupleDesc(types,names);
    }

    public TupleDesc getTupleDesc() {
        return desc;
    }

    public void open() throws DbException, TransactionAbortedException {
        super.open();
        childIt.open();
        inserted = false;
    }

    public void close() {
        super.close();
        childIt.close();
    }

    public void rewind() throws DbException, TransactionAbortedException {
        childIt.rewind();
    }

    /**
     * Inserts tuples read from child into the tableid specified by the
     * constructor. It returns a one field tuple containing the number of
     * inserted records. Inserts should be passed through BufferPool. An
     * instances of BufferPool is available via Database.getBufferPool(). Note
     * that insert DOES NOT need check to see if a particular tuple is a
     * duplicate before inserting it.
     * 
     * @return A 1-field tuple containing the number of inserted records, or
     *         null if called more than once.
     * @see Database#getBufferPool
     * @see BufferPool#insertTuple
     */
    protected Tuple fetchNext() throws TransactionAbortedException, DbException {
        if (inserted){
            return null;
        }
        Tuple tup;
        int count = 0;
        while(childIt.hasNext()){
            tup = childIt.next();
            try {
                Database.getBufferPool().insertTuple(tid,tableId,tup);
            } catch (IOException e) {
                throw new DbException("IOexception on tuple insertion");
            }
            ++count;
        }
        Tuple result = new Tuple(desc);
        result.setField(0,new IntField(count));
        inserted = true;
        return result;
    }

    @Override
    public DbIterator[] getChildren() {
        // some code goes here
        return new DbIterator[] {childIt};
    }

    @Override
    public void setChildren(DbIterator[] children) {
        childIt = children[0];
    }
}
