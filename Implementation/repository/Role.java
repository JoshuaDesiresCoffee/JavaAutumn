package Implementation.repository;

import Autumn.orm.Id;
import Autumn.orm.Table;

@Table
public class Role {
    @Id
    public int id;
    public String displayedAs; // "GUEST", "AUTH", "ADMIN"
}
