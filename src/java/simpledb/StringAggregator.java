package simpledb;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Knows how to compute some aggregate over a set of StringFields.
 */
public class StringAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;

    /**
     * Aggregate constructor
     * @param gbfield the 0-based index of the group-by field in the tuple, or NO_GROUPING if there is no grouping
     * @param gbfieldtype the type of the group by field (e.g., Type.INT_TYPE), or null if there is no grouping
     * @param afield the 0-based index of the aggregate field in the tuple
     * @param what aggregation operator to use -- only supports COUNT
     * @throws IllegalArgumentException if what != COUNT
     */

    public int gFieldIndex;
    public Type groupType;
    public int aFieldIndex;
    public Op op;

    public HashMap<Field,Integer> groups;

    public StringAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
        gFieldIndex = gbfield;
        groupType = gbfieldtype;
        aFieldIndex = afield;
        op = what;
        if (op!=Op.COUNT){
            throw new IllegalArgumentException("Only Count is Supported");
        }
        groups = new HashMap<Field, Integer>();
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the constructor
     * @param tup the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
        Field tupleGroupField;
        if(gFieldIndex == NO_GROUPING){
            tupleGroupField = null;
        }
        else {
            tupleGroupField = tup.getField(gFieldIndex);
        }

        if (!groups.containsKey(tupleGroupField)){
            groups.put(tupleGroupField,1);
        }
        else {
            groups.put(tupleGroupField,groups.get(tupleGroupField) + 1);
        }
    }

    private TupleDesc groupTupleDesc()
    {
        String[] names;
        Type[] types;
        if (gFieldIndex == Aggregator.NO_GROUPING)
        {
            names = new String[] {"aggregateValue"};
            types = new Type[] {Type.INT_TYPE};
        }
        else
        {
            names = new String[] {"groupValue", "aggregateValue"};
            types = new Type[] {groupType, Type.INT_TYPE};
        }
        return new TupleDesc(types, names);
    }

    /**
     * Create a DbIterator over group aggregate results.
     *
     * @return a DbIterator whose tuples are the pair (groupVal,
     *   aggregateVal) if using group, or a single (aggregateVal) if no
     *   grouping. The aggregateVal is determined by the type of
     *   aggregate specified in the constructor.
     */
    public DbIterator iterator() {
        ArrayList<Tuple> tuples = new ArrayList<Tuple>();
        TupleDesc groupDesc = groupTupleDesc();
        Tuple newTup;
        int aggregateVal;
        for (Field g : groups.keySet()){

            aggregateVal = groups.get(g);
            newTup = new Tuple(groupDesc);
            if (gFieldIndex == Aggregator.NO_GROUPING){
                newTup.setField(0, new IntField(aggregateVal));
            }
            else{
                newTup.setField(0,g);
                newTup.setField(1, new IntField(aggregateVal));
            }
            tuples.add(newTup);
        }
        return new TupleIterator(groupDesc,tuples);
    }
}
