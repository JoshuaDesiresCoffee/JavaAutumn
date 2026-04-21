package Implementation.repository;

import Autumn.orm.Id;
import Autumn.orm.Table;

@Table
public class Provenance {
    @Id
    public int id;
    public String displayedAs;
}
