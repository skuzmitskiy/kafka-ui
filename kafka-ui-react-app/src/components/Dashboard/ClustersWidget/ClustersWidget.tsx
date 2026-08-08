import React from 'react';
import * as Metrics from 'components/common/Metrics';
import { Tag } from 'components/common/Tag/Tag.styled';
import Switch from 'components/common/Switch/Switch';
import { useClusters } from 'lib/hooks/api/clusters';
import { Cluster, ServerStatus } from 'generated-sources';
import { ColumnDef } from '@tanstack/react-table';
import Table, { SizeCell } from 'components/common/NewTable';
import { Button } from 'components/common/Button/Button';
import { clusterNewConfigPath } from 'lib/paths';
import { useCurrentUser } from 'lib/hooks/api/users';
import { GlobalSettingsContext } from 'components/contexts/GlobalSettingsContext';
import ClusterTableActionsCell from 'components/Dashboard/ClusterTableActionsCell';

import * as S from './ClustersWidget.styled';
import ClusterName from './ClusterName';

const ClustersWidget: React.FC = () => {
  const { data } = useClusters();
  const { data: currentUser } = useCurrentUser();
  const appInfo = React.useContext(GlobalSettingsContext);
  const [showOfflineOnly, setShowOfflineOnly] = React.useState<boolean>(false);

  const canConfigure =
    appInfo.hasDynamicConfig && currentUser?.role === 'READ_WRITE';

  const config = React.useMemo(() => {
    const clusters = data || [];
    const offlineClusters = clusters.filter(
      ({ status }) => status === ServerStatus.OFFLINE
    );
    return {
      list: showOfflineOnly ? offlineClusters : clusters,
      online: clusters.length - offlineClusters.length,
      offline: offlineClusters.length,
    };
  }, [data, showOfflineOnly]);

  const columns = React.useMemo<ColumnDef<Cluster>[]>(() => {
    const initialColumns: ColumnDef<Cluster>[] = [
      { header: 'Cluster name', accessorKey: 'name', cell: ClusterName },
      { header: 'Version', accessorKey: 'version' },
      { header: 'Brokers count', accessorKey: 'brokerCount' },
      { header: 'Partitions', accessorKey: 'onlinePartitionCount' },
      { header: 'Topics', accessorKey: 'topicCount' },
      { header: 'Production', accessorKey: 'bytesInPerSec', cell: SizeCell },
      { header: 'Consumption', accessorKey: 'bytesOutPerSec', cell: SizeCell },
    ];

    if (appInfo.hasDynamicConfig) {
      initialColumns.push({
        header: '',
        id: 'actions',
        cell: ClusterTableActionsCell,
      });
    }

    return initialColumns;
  }, [appInfo.hasDynamicConfig]);

  const handleSwitch = () => setShowOfflineOnly(!showOfflineOnly);
  return (
    <>
      <Metrics.Wrapper>
        <Metrics.Section>
          <Metrics.Indicator label={<Tag color="green">Online</Tag>}>
            <span>{config.online}</span>{' '}
            <Metrics.LightText>clusters</Metrics.LightText>
          </Metrics.Indicator>
          <Metrics.Indicator label={<Tag color="gray">Offline</Tag>}>
            <span>{config.offline}</span>{' '}
            <Metrics.LightText>clusters</Metrics.LightText>
          </Metrics.Indicator>
        </Metrics.Section>
      </Metrics.Wrapper>
      <S.Toolbar>
        <div>
          <Switch
            name="switchRoundedDefault"
            checked={showOfflineOnly}
            onChange={handleSwitch}
          />
          <label>Only offline clusters</label>
        </div>
        {canConfigure && (
          <Button buttonType="primary" buttonSize="M" to={clusterNewConfigPath}>
            Configure new cluster
          </Button>
        )}
      </S.Toolbar>
      <Table
        columns={columns}
        data={config?.list}
        enableSorting
        emptyMessage="Disk usage data not available"
      />
    </>
  );
};

export default ClustersWidget;
