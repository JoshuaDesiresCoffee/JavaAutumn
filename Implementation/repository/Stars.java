package Implementation.repository;

import Autumn.orm.Id;
import Autumn.orm.Table;

@Table
public class Stars {
    @Id
    public int id;
    public String displayedAs; // e.g. "•", "••", "•••", "••••", "•••••"
    public int value;           // numeric value 1-5
}
