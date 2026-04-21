package Implementation.repository;

import Autumn.orm.Id;
import Autumn.orm.Table;

@Table
public class Epoch {
    @Id
    public int id;
    public String displayedAs;
}
