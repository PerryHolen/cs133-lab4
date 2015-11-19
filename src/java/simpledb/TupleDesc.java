package simpledb;

import java.io.Serializable;
import java.util.*;

/**
 * TupleDesc describes the schema of a tuple.
 */
public class TupleDesc implements Serializable {

    /**
     * A help class to facilitate organizing the information of each field
     * */
    public static class TDItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * The type of the field
         * */
        public final Type fieldType;
        
        /**
         * The name of the field
         * */
        public final String fieldName;

        public TDItem(Type t, String n) {
            this.fieldName = n;
            this.fieldType = t;
        }

        public String toString() {
            return fieldName + "(" + fieldType + ")";
        }
    }


    private Vector<TDItem> tdItems;
    /**
     * @return
     *        An iterator which iterates over all the field TDItems
     *        that are included in this TupleDesc
     * */
    public Iterator<TDItem> iterator() {
        return tdItems.iterator();
    }

    private static final long serialVersionUID = 1L;

    /**
     * Create a new TupleDesc with typeAr.length fields with fields of the
     * specified types, with associated named fields.
     * 
     * @param typeAr
     *            array specifying the number of and types of fields in this
     *            TupleDesc. It must contain at least one entry.
     * @param fieldAr
     *            array specifying the names of the fields. Note that names may
     *            be null.
     */
    public TupleDesc(Type[] typeAr, String[] fieldAr) {
        tdItems = new Vector<TDItem>();
        for(int i =0; i< typeAr.length; ++i){
            tdItems.add(new TDItem(typeAr[i],fieldAr[i]));
        }
    }

    /**
     * Constructor. Create a new tuple desc with typeAr.length fields with
     * fields of the specified types, with anonymous (unnamed) fields.
     * 
     * @param typeAr
     *            array specifying the number of and types of fields in this
     *            TupleDesc. It must contain at least one entry.
     */
    public TupleDesc(Type[] typeAr) {
        tdItems = new Vector<TDItem>();
        for (int i = 0; i < typeAr.length; i++) {
            tdItems.add(new TDItem(typeAr[i], null));
        }
    }

    /**
     * @return the number of fields in this TupleDesc
     */
    public int numFields() {
        return tdItems.size();
    }

    /**
     * Gets the (possibly null) field name of the ith field of this TupleDesc.
     * 
     * @param i
     *            index of the field name to return. It must be a valid index.
     * @return the name of the ith field
     * @throws NoSuchElementException
     *             if i is not a valid field reference.
     */
    public String getFieldName(int i) throws NoSuchElementException {
        if (i>=numFields() || i<0){
            throw new NoSuchElementException("Out of Bounds");
        }
        else{
            return tdItems.get(i).fieldName;
        }
    }

    /**
     * Gets the type of the ith field of this TupleDesc.
     * 
     * @param i
     *            The index of the field to get the type of. It must be a valid
     *            index.
     * @return the type of the ith field
     * @throws NoSuchElementException
     *             if i is not a valid field reference.
     */
    public Type getFieldType(int i) throws NoSuchElementException {
        if (i>=numFields() || i<0){
            throw new NoSuchElementException("Out of Bounds");
        }
        else{
            return tdItems.get(i).fieldType;
        }
    }

    /**
     * Find the index of the field with a given name.
     * 
     * @param name
     *            name of the field.
     * @return the index of the field that is first to have the given name.
     * @throws NoSuchElementException
     *             if no field with a matching name is found.
     */
    public int fieldNameToIndex(String name) throws NoSuchElementException {

        if (name == null){
            throw new NoSuchElementException("Input name is null");
        }
        for (int i = 0; i < numFields(); i++) {
            String fieldName = getFieldName(i);
            if (fieldName == null){
                continue;
            }
            if (getFieldName(i).equals(name)){
                return i;
            }
        }
        throw new NoSuchElementException("Element does not Exist");
    }

    /**
     * @return The size (in bytes) of tuples corresponding to this TupleDesc.
     *         Note that tuples from a given TupleDesc are of a fixed size.
     */
    public int getSize() {
        int size = 0;
        for(TDItem item : tdItems){
            size+=item.fieldType.getLen();
        }
        return size;
    }

    /**
     * Merge two TupleDescs into one, with td1.numFields + td2.numFields fields,
     * with the first td1.numFields coming from td1 and the remaining from td2.
     * 
     * @param td1
     *            The TupleDesc with the first fields of the new TupleDesc
     * @param td2
     *            The TupleDesc with the last fields of the TupleDesc
     * @return the new TupleDesc
     */
    public static TupleDesc merge(TupleDesc td1, TupleDesc td2) {
        int newSize = td1.numFields() + td2.numFields();
        Type[] newTypes = new Type[newSize];
        String[] newNames = new String[newSize];
        for (int i = 0; i < td1.numFields(); i++){
            newTypes[i] = td1.getFieldType(i);
            newNames[i] = td1.getFieldName(i);
        }
        for (int i = 0; i < td2.numFields(); i++) {
            newTypes[i + td1.numFields()] = td2.getFieldType(i);
            newNames[i + td1.numFields()] = td2.getFieldName(i);
        }
        // some code goes here
        return new TupleDesc(newTypes, newNames);
    }



    /**
     * Compares the specified object with this TupleDesc for equality. Two
     * TupleDescs are considered equal if they are the same size and if the n-th
     * type in this TupleDesc is equal to the n-th type in td.
     * 
     * @param o
     *            the Object to be compared for equality with this TupleDesc.
     * @return true if the object is equal to this TupleDesc.
     */
    public boolean equals(Object o) {
        if(o == null){
            return false;
        }
        if(!(o instanceof TupleDesc)){
            return false;
        }
        TupleDesc oTuple = (TupleDesc) o;
        if (getSize()!=oTuple.getSize() || numFields()!=oTuple.numFields()){
            return false;
        }

        for (int i = 0; i < numFields(); i++) {
            if (getFieldType(i)!=oTuple.getFieldType(i)){
                return false;
            }
        }
        return true;
    }

    public int hashCode() {
        return toString().hashCode();
    }

    /**
     * Returns a String describing this descriptor. It should be of the form
     * "fieldType[0](fieldName[0]), ..., fieldType[M](fieldName[M])", although
     * the exact format does not matter.
     * 
     * @return String describing this descriptor.
     */
    public String toString() {
        StringBuffer sBuf = new StringBuffer();
        for (int i = 0; i < numFields(); i++) {
            sBuf.append("( [" + i + "]: TYPE: " + getFieldType(i).toString()+", NAME: " + getFieldName(i)+"  )");
        }
        return sBuf.toString();
    }
}
