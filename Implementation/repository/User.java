package Implementation.repository;

import Autumn.orm.annotations.Id;
import Autumn.orm.annotations.OneToMany;
import Autumn.orm.Table;

import java.util.List;

@Table
public class User {
    @Id
    public int id;
    public String name;
    public String email;

    /** Populated only when the query includes {@code JOIN(Rating.class)}; otherwise null. */
    @OneToMany public List<Rating> ratings;

    /** Populated only when the query includes {@code JOIN(UserRole.class)}; otherwise null. */
    @OneToMany public List<UserRole> userRoles;

    @Override public String toString() {
        return "User{id=" + id + ", name=" + name + ", email=" + email + "}";
    }
}
