import styled from 'styled-components';

export const Page = styled.main`
  width: 100%;
  padding: 24px;
`;

export const Form = styled.form`
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(180px, 1fr) 160px auto;
  gap: 12px;
  margin: 24px 0;
  align-items: end;
`;

export const Field = styled.label`
  display: grid;
  gap: 6px;
  font-size: 13px;
`;

export const Input = styled.input`
  height: 36px;
  padding: 0 10px;
  border: 1px solid ${({ theme }) => theme.input.borderColor.normal};
  border-radius: 4px;
`;

export const Select = styled.select`
  height: 36px;
  padding: 0 10px;
`;

export const Table = styled.table`
  width: 100%;
  border-collapse: collapse;

  th,
  td {
    padding: 12px;
    border-bottom: 1px solid ${({ theme }) => theme.input.borderColor.normal};
    text-align: left;
  }
`;

export const Actions = styled.div`
  display: flex;
  gap: 8px;
`;

export const Error = styled.p`
  color: ${({ theme }) => theme.alert.color.error};
  margin: 12px 0;
`;
