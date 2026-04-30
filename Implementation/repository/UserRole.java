package Implementation.repository;

import Autumn.orm.ForeignKey;
import Autumn.orm.Id;
import Autumn.orm.Table;

@Table
public class UserRole {
    @Id
    public int id;
    @ForeignKey(table = User.class)
    public int userId;
    @ForeignKey(table = Role.class)
    public int roleId;
}
