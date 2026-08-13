import React, { FormEvent, useState } from 'react';
import { Button } from 'components/common/Button/Button';
import PageHeading from 'components/common/PageHeading/PageHeading';
import {
  UserRole,
  useCreateUser,
  useCurrentUser,
  useDeleteUser,
  useUpdateUser,
  useUsers,
} from 'lib/hooks/api/users';

import * as S from './Users.styled';

const Users: React.FC = () => {
  const currentUser = useCurrentUser();
  const users = useUsers();
  const createUser = useCreateUser();
  const updateUser = useUpdateUser();
  const deleteUser = useDeleteUser();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<UserRole>('READ');
  const [newPasswords, setNewPasswords] = useState<Record<string, string>>({});

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    await createUser.mutateAsync({ username, password, role });
    setUsername('');
    setPassword('');
    setRole('READ');
  };

  const changeRole = (name: string, newRole: UserRole) => {
    updateUser.mutate({ username: name, role: newRole });
  };

  const error =
    createUser.error || updateUser.error || deleteUser.error || users.error;

  return (
    <S.Page>
      <PageHeading text="User accounts" />
      <p>
        Read users can view Kafka data. Read/write users can also change Kafka
        data and manage accounts.
      </p>
      <S.Form onSubmit={submit}>
        <S.Field>
          Username
          <S.Input
            value={username}
            minLength={3}
            required
            onChange={(event) => setUsername(event.target.value)}
          />
        </S.Field>
        <S.Field>
          Password
          <S.Input
            value={password}
            type="password"
            minLength={8}
            required
            onChange={(event) => setPassword(event.target.value)}
          />
        </S.Field>
        <S.Field>
          Permission
          <S.Select
            value={role}
            onChange={(event) => setRole(event.target.value as UserRole)}
          >
            <option value="READ">Read</option>
            <option value="READ_WRITE">Read / write</option>
          </S.Select>
        </S.Field>
        <Button
          type="submit"
          buttonType="primary"
          buttonSize="M"
          disabled={createUser.isLoading}
        >
          Create
        </Button>
      </S.Form>
      {!!error && <S.Error>{(error as Error).message}</S.Error>}
      <S.Table>
        <thead>
          <tr>
            <th>Username</th>
            <th>Permission</th>
            <th>New password</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {users.data?.map((user) => (
            <tr key={user.username}>
              <td>{user.username}</td>
              <td>
                <S.Select
                  value={user.role}
                  onChange={(event) =>
                    changeRole(user.username, event.target.value as UserRole)
                  }
                >
                  <option value="READ">Read</option>
                  <option value="READ_WRITE">Read / write</option>
                </S.Select>
              </td>
              <td>
                <S.Input
                  type="password"
                  minLength={8}
                  placeholder="Leave unchanged"
                  value={newPasswords[user.username] || ''}
                  onChange={(event) =>
                    setNewPasswords({
                      ...newPasswords,
                      [user.username]: event.target.value,
                    })
                  }
                />
              </td>
              <td>
                <S.Actions>
                  <Button
                    type="button"
                    buttonType="primary"
                    buttonSize="M"
                    disabled={(newPasswords[user.username] || '').length < 8}
                    onClick={() => {
                      updateUser.mutate({
                        username: user.username,
                        password: newPasswords[user.username],
                        role: user.role,
                      });
                      setNewPasswords({ ...newPasswords, [user.username]: '' });
                    }}
                  >
                    Set password
                  </Button>
                  <Button
                    type="button"
                    buttonType="secondary"
                    buttonSize="M"
                    disabled={currentUser.data?.username === user.username}
                    onClick={() => deleteUser.mutate(user.username)}
                  >
                    Delete
                  </Button>
                </S.Actions>
              </td>
            </tr>
          ))}
        </tbody>
      </S.Table>
    </S.Page>
  );
};

export default Users;
