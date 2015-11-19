package simpledb;

import java.io.IOException;

/**
 * The delete operator. Delete reads tuples from its child operator and removes
 * them from the table they belong to.
 */
public class Delete extends Operator {

    private static final long serialVersionUID = 1L;

    private TransactionId tid;
    private DbIterator childIt;
    private boolean deleted;
    private TupleDesc desc;

    /**
     * Constructor specifying the transaction that this delete belongs to as
     * well as the child to read from.
     * 
     * @param t
     *            The transaction this delete runs in
     * @param child
     *            The child operator from which to read tuples for deletion
     */
    public Delete(TransactionId t, DbIterator child) {
        tid = t;
        childIt = child;
        deleted = false;

        String[] names = new String[]{"Deleted"};
        Type[] types = new Type[] {Type.INT_TYPE};
        desc = new TupleDesc(types,names);
    }

    public TupleDesc getTupleDesc() {
        return desc;
    }

    public void open() throws DbException, TransactionAbortedException {
        super.open();
        childIt.open();
        deleted = false;
    }

    public void close() {
        super.close();
        childIt.close();
    }

    public void rewind() throws DbException, TransactionAbortedException {
        childIt.rewind();
    }

    /**
     * Deletes tuples as they are read from the child operator. Deletes are
     * processed via the buffer pool (which can be accessed via the
     * Database.getBufferPool() method.
     * 
     * @return A 1-field tuple containing the number of deleted records.
     * @see Database#getBufferPool
     * @see BufferPool#deleteTuple
     */
    protected Tuple fetchNext() throws TransactionAbortedException, DbException {
        if (deleted){
            return null;
        }
        Tuple tup;
        int count = 0;
        while (childIt.hasNext()){
            tup = childIt.next();

            try{
                Database.getBufferPool().deleteTuple(tid,tup);
            } catch (IOException e) {
                throw new DbException("IO exception on delete");
            }
            ++count;
        }
        Tuple result = new Tuple(desc);
        result.setField(0,new IntField(count));
        deleted = true;
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
