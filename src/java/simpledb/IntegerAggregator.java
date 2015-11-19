package simpledb;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Knows how to compute some aggregate over a set of IntFields.
 */
public class IntegerAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;

    public int gFieldIndex;
    public Type groupType;
    public int aFieldIndex;
    public Op op;

    public HashMap<Field,Integer> groups;
    public HashMap<Field, Integer> counts;

    /**
     * Aggregate constructor
     *
     * @param gbfield
     *            the 0-based index of the group-by field in the tuple, or
     *            NO_GROUPING if there is no grouping
     * @param gbfieldtype
     *            the type of the group by field (e.g., Type.INT_TYPE), or null
     *            if there is no grouping
     * @param afield
     *            the 0-based index of the aggregate field in the tuple
     * @param what
     *            the aggregation operator
     */

    public IntegerAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
        gFieldIndex = gbfield;
        groupType = gbfieldtype;
        aFieldIndex = afield;
        op = what;
        groups = new HashMap<Field, Integer>();
        counts = new HashMap<Field, Integer>();
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the
     * constructor
     *
     * @param tup
     *            the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
        Field tupleGroupField;
        if(gFieldIndex == NO_GROUPING){
            tupleGroupField = null;
        }
        else{
            tupleGroupField = tup.getField(gFieldIndex);
        }

        if (!groups.containsKey(tupleGroupField)){
            groups.put(tupleGroupField,0);
            counts.put(tupleGroupField, 0);
            if (op == Op.MAX){
                groups.put(tupleGroupField,Integer.MIN_VALUE);
            }
            if (op == Op.MIN){
                groups.put(tupleGroupField,Integer.MAX_VALUE);
            }
        }
        int oldCount = counts.get(tupleGroupField);
        counts.put(tupleGroupField,oldCount+1);

        int tupleValue = ((IntField)tup.getField(aFieldIndex)).getValue();
        int oldValue = groups.get(tupleGroupField);
        int newValue = oldValue;
        switch (op){
            case MAX:
                newValue = Math.max(tupleValue,oldValue);
                break;
            case MIN:
                newValue = Math.min(tupleValue,oldValue);
                break;
            case AVG:
                newValue = oldValue + tupleValue;
                break;
            case SUM:
                newValue = oldValue + tupleValue;
                break;
            case COUNT:
                newValue = oldValue+1;
                break;
            default:
                break;
        }
        groups.put(tupleGroupField,newValue);

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
     * @return a DbIterator whose tuples are the pair (groupVal, aggregateVal)
     *         if using group, or a single (aggregateVal) if no grouping. The
     *         aggregateVal is determined by the type of aggregate specified in
     *         the constructor.
     */
    public DbIterator iterator() {
        ArrayList<Tuple> tuples = new ArrayList<Tuple>();
        TupleDesc groupDesc = groupTupleDesc();
        Tuple newTup;
        int aggregateVal;
        for (Field g : groups.keySet()){
            if (op == Op.AVG){
                aggregateVal = groups.get(g)/counts.get(g);
            }
            else{
                aggregateVal = groups.get(g);
            }

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
