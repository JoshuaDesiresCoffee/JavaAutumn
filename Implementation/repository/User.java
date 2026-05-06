package Implementation.repository;

import Autumn.orm.annotations.Id;
import Autumn.orm.Table;

@Table
public class User {
    @Id
    public int id;
    public String name;
    public String email;

    @Override public String toString() {
        return "User{id=" + id + ", name=" + name + ", email=" + email + "}";
    }
}
