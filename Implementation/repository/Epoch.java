package Implementation.repository;

import Autumn.orm.annotations.Id;
import Autumn.orm.Table;

@Table
public class Epoch {
    @Id
    public int id;
    public String displayedAs;
}
