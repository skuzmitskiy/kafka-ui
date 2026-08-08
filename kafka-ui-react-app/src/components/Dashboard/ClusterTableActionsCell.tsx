import React from 'react';
import { Cluster } from 'generated-sources';
import { CellContext } from '@tanstack/react-table';
import { clusterConfigPath } from 'lib/paths';
import { useCurrentUser } from 'lib/hooks/api/users';
import { Button } from 'components/common/Button/Button';

type Props = CellContext<Cluster, unknown>;

const ClusterTableActionsCell: React.FC<Props> = ({ row }) => {
  const { name } = row.original;
  const { data: currentUser } = useCurrentUser();

  if (currentUser?.role !== 'READ_WRITE') {
    return null;
  }

  return (
    <Button buttonType="secondary" buttonSize="S" to={clusterConfigPath(name)}>
      Configure
    </Button>
  );
};

export default ClusterTableActionsCell;
