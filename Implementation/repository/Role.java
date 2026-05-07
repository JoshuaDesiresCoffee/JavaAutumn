package Implementation.repository;

import Autumn.orm.annotations.Id;
import Autumn.orm.annotations.OneToMany;
import Autumn.orm.Table;

import java.util.List;

@Table
public class Role {
    @Id
    public int id;
    public String displayedAs; // "GUEST", "AUTH", "ADMIN"

    /** Populated only when the query includes {@code JOIN(UserRole.class)}; otherwise null. */
    @OneToMany public List<UserRole> userRoles;
}
