package simpledb;

import java.util.*;

/**
 * The Aggregation operator that computes an aggregate (e.g., sum, avg, max,
 * min). Note that we only support aggregates over a single column, grouped by a
 * single column.
 */
public class Aggregate extends Operator {

    private static final long serialVersionUID = 1L;

    private DbIterator childIt;
    private int aFieldIndex;
    private int gFieldIndex;
    private Aggregator.Op op;
    private Aggregator aggregator;
    private DbIterator aggregateIt;


    /**
     * Constructor.
     *
     * Implementation hint: depending on the type of afield, you will want to
     * construct an {@link IntegerAggregator} or {@link StringAggregator} to help
     * you with your implementation of readNext().
     *
     *
     * @param child
     *            The DbIterator that is feeding us tuples.
     * @param afield
     *            The column over which we are computing an aggregate.
     * @param gfield
     *            The column over which we are grouping the result, or -1 if
     *            there is no grouping
     * @param aop
     *            The aggregation operator to use
     */
    public Aggregate(DbIterator child, int afield, int gfield, Aggregator.Op aop) {
        childIt = child;
        aFieldIndex = afield;
        gFieldIndex = gfield;
        op = aop;
        aggregateIt = null;

        Type groupByType;
        if (gFieldIndex == Aggregator.NO_GROUPING){
            groupByType = null;
        }
        else {
            groupByType = childIt.getTupleDesc().getFieldType(gFieldIndex);
        }
        Type aggregateType = childIt.getTupleDesc().getFieldType(aFieldIndex);
        if (aggregateType == Type.INT_TYPE){
            aggregator = new IntegerAggregator(gFieldIndex,groupByType,aFieldIndex,op);
        }
        if (aggregateType == Type.STRING_TYPE){
            aggregator = new StringAggregator(gFieldIndex,groupByType,aFieldIndex,op);
        }

    }

    /**
     * @return If this aggregate is accompanied by a groupby, return the groupby
     *         field index in the <b>INPUT</b> tuples. If not, return
     *         {@link simpledb.Aggregator#NO_GROUPING}
     * */
    public int groupField() {
	return gFieldIndex;
    }

    /**
     * @return If this aggregate is accompanied by a group by, return the name
     *         of the groupby field in the <b>OUTPUT</b> tuples If not, return
     *         null;
     * */
    public String groupFieldName() {
        if (groupField() == Aggregator.NO_GROUPING){
            return null;
        }
        else{
            return childIt.getTupleDesc().getFieldName(groupField());
        }
    }

    /**
     * @return the aggregate field
     * */
    public int aggregateField() {
	return aFieldIndex;
    }

    /**
     * @return return the name of the aggregate field in the <b>OUTPUT</b>
     *         tuples
     * */
    public String aggregateFieldName() {
	return childIt.getTupleDesc().getFieldName(aggregateField());
    }

    /**
     * @return return the aggregate operator
     * */
    public Aggregator.Op aggregateOp() {
	return op;
    }

    public static String nameOfAggregatorOp(Aggregator.Op aop) {
	return aop.toString();
    }

    public void open() throws NoSuchElementException, DbException,
	    TransactionAbortedException {
        super.open();
        childIt.open();
        while(childIt.hasNext()){
            aggregator.mergeTupleIntoGroup(childIt.next());
        }
        aggregateIt = aggregator.iterator();
        aggregateIt.open();
    }

    /**
     * Returns the next tuple. If there is a group by field, then the first
     * field is the field by which we are grouping, and the second field is the
     * result of computing the aggregate, If there is no group by field, then
     * the result tuple should contain one field representing the result of the
     * aggregate. Should return null if there are no more tuples.
     */
    protected Tuple fetchNext() throws TransactionAbortedException, DbException {
        if (aggregateIt.hasNext()){
            return aggregateIt.next();
        }
        return null;
    }

    public void rewind() throws DbException, TransactionAbortedException {
        aggregateIt.rewind();
    }

    /**
     * Returns the TupleDesc of this Aggregate. If there is no group by field,
     * this will have one field - the aggregate column. If there is a group by
     * field, the first field will be the group by field, and the second will be
     * the aggregate value column.
     *
     * The name of an aggregate column should be informative. For example:
     * "aggName(aop) (child_td.getFieldName(afield))" where aop and afield are
     * given in the constructor, and child_td is the TupleDesc of the child
     * iterator.
     */
    public TupleDesc getTupleDesc() {
	// some code goes here
	return childIt.getTupleDesc();
    }

    public void close() {
	    super.close();
        childIt.close();
        aggregateIt.close();
    }

    @Override
    public DbIterator[] getChildren() {
	// some code goes here
	return new DbIterator[] {aggregateIt};
    }

    @Override
    public void setChildren(DbIterator[] children) {
	    aggregateIt = children[0];
    }

}
