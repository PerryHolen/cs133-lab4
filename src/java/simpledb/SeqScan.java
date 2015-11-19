package simpledb;

import javax.xml.crypto.Data;
import java.util.*;

/**
 * SeqScan is an implementation of a sequential scan access method that reads
 * each tuple of a table in no particular order (e.g., as they are laid out on
 * disk).
 */
public class SeqScan implements DbIterator {

    private static final long serialVersionUID = 1L;

    public int tableId;
    public TransactionId tId;
    public String tAlias;
    public DbFileIterator it;

    /**
     * Creates a sequential scan over the specified table as a part of the
     * specified transaction.
     * 
     * @param tid
     *            The transaction this scan is running as a part of.
     * @param tableid
     *            the table to scan.
     * @param tableAlias
     *            the alias of this table (needed by the parser); the returned
     *            tupleDesc should have fields with name tableAlias.fieldName
     *            (note: this class is not responsible for handling a case where
     *            tableAlias or fieldName are null. It shouldn't crash if they
     *            are, but the resulting name can be null.fieldName,
     *            tableAlias.null, or null.null).
     */
    public SeqScan(TransactionId tid, int tableid, String tableAlias) {
        tId = tid;
        tableId = tableid;
        tAlias = tableAlias;
        it = Database.getCatalog().getDatabaseFile(tableId).iterator(tId);
    }

    /**
     * @return
     *       return the table name of the table the operator scans. This should
     *       be the actual name of the table in the catalog of the database
     * */
    public String getTableName() {
        return Database.getCatalog().getTableName(tableId);
    }
    
    /**
     * @return Return the alias of the table this operator scans. 
     * */
    public String getAlias()
    {
        return tAlias;
    }

    /**
     * Reset the tableid, and tableAlias of this operator.
     * @param tableid
     *            the table to scan.
     * @param tableAlias
     *            the alias of this table (needed by the parser); the returned
     *            tupleDesc should have fields with name tableAlias.fieldName
     *            (note: this class is not responsible for handling a case where
     *            tableAlias or fieldName are null. It shouldn't crash if they
     *            are, but the resulting name can be null.fieldName,
     *            tableAlias.null, or null.null).
     */
    public void reset(int tableid, String tableAlias) {
        tableId = tableid;
        tAlias = tableAlias;
        it= Database.getCatalog().getDatabaseFile(tableId).iterator(tId);
    }

    public SeqScan(TransactionId tid, int tableid) {
        this(tid, tableid, Database.getCatalog().getTableName(tableid));
    }

    public void open() throws DbException, TransactionAbortedException {
        if (it!=null) {
            it.open();
        }
    }

    /**
     * Returns the TupleDesc with field names from the underlying HeapFile,
     * prefixed with the tableAlias string from the constructor. This prefix
     * becomes useful when joining tables containing a field(s) with the same
     * name.
     * 
     * @return the TupleDesc with field names from the underlying HeapFile,
     *         prefixed with the tableAlias string from the constructor.
     */
    public TupleDesc getTupleDesc() {
        TupleDesc tCopy = Database.getCatalog().getTupleDesc(tableId);
        int size = tCopy.numFields();
        Type[] types = new Type[size];
        String[] fields = new String[size];
        for (int i = 0; i < size; i++) {
            types[i] = tCopy.getFieldType(i);
            fields[i] = tAlias+"."+tCopy.getFieldName(i);
        }
        return new TupleDesc(types,fields);
    }

    public boolean hasNext() throws TransactionAbortedException, DbException {
        return it!=null && it.hasNext();
    }

    public Tuple next() throws NoSuchElementException,
            TransactionAbortedException, DbException {
        if(it!=null){
            return it.next();
        }
        else{
            return null;
        }
    }

    public void close() {
        if (it!= null) {
            it.close();
        }
    }

    public void rewind() throws DbException, NoSuchElementException,
            TransactionAbortedException {
        if (it!= null) {
            it.rewind();
        }
    }
}
