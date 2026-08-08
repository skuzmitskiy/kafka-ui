import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

export type UserRole = 'READ' | 'READ_WRITE';

export interface LocalUser {
  username: string;
  role: UserRole;
}

export interface LocalUserRequest {
  username: string;
  password?: string;
  role: UserRole;
}

const request = async <T>(url: string, init?: RequestInit): Promise<T> => {
  const response = await fetch(url, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  });
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message || `Request failed (${response.status})`);
  }
  return response.status === 204
    ? (undefined as unknown as T)
    : response.json();
};

export const useCurrentUser = () =>
  useQuery(['currentUser'], () => request<LocalUser>('/api/auth/me'), {
    retry: false,
  });

export const useUsers = () =>
  useQuery(['localUsers'], () => request<LocalUser[]>('/api/auth/users'));

export const useCreateUser = () => {
  const client = useQueryClient();
  return useMutation(
    (user: LocalUserRequest) =>
      request<LocalUser>('/api/auth/users', {
        method: 'POST',
        body: JSON.stringify(user),
      }),
    { onSuccess: () => client.invalidateQueries(['localUsers']) }
  );
};

export const useUpdateUser = () => {
  const client = useQueryClient();
  return useMutation(
    (user: LocalUserRequest) =>
      request<LocalUser>(
        `/api/auth/users/${encodeURIComponent(user.username)}`,
        {
          method: 'PUT',
          body: JSON.stringify(user),
        }
      ),
    { onSuccess: () => client.invalidateQueries(['localUsers']) }
  );
};

export const useDeleteUser = () => {
  const client = useQueryClient();
  return useMutation(
    (username: string) =>
      request<void>(`/api/auth/users/${encodeURIComponent(username)}`, {
        method: 'DELETE',
      }),
    { onSuccess: () => client.invalidateQueries(['localUsers']) }
  );
};
