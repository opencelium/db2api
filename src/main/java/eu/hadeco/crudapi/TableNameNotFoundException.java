package eu.hadeco.crudapi;

public class TableNameNotFoundException extends ClassNotFoundException {
    public TableNameNotFoundException(String txt) {
        super(txt);
    }
}
