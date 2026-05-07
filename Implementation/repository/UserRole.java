package Implementation.repository;

import Autumn.orm.annotations.Id;
import Autumn.orm.Table;

@Table
public class UserRole {
    @Id
    public int id;
    public User user;
    public Role role;
}
