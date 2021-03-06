import React from "react";
import { renderOperationBtns, NavRouterLink } from "container/custom-component";
import { InfoCircleOutlined } from "@ant-design/icons";
import { message, Tag, Modal, Progress, Tooltip, notification, DatePicker } from "antd";
import { IClusterStatus, IOpLogicCluster, IOpPhysicsCluster } from "typesPath/cluster/cluster-types";
import { nounClusterType, nounClusterStatus } from "container/tooltip";
import {
  ClusterAuth,
  ClusterAuthMaps,
  ClusterStatus,
  clusterTypeMap,
  INDEX_AUTH_TYPE_MAP,
  logicClusterType,
  PHY_CLUSTER_TYPE,
  VERSION_MAINFEST_TYPE,
  StatusMap,
} from "constants/status-map";
import { cellStyle } from "constants/table";
import { delPackage } from "api/cluster-api";
import { ITableBtn } from "component/dantd/dtable";
import { IVersions } from "typesPath/cluster/physics-type";
import moment from "moment";
import { timeFormat } from "constants/time";
import { submitWorkOrder } from "api/common-api";
import { IWorkOrder } from "typesPath/params-types";

import store from "store";
import { updateBinCluster } from "api/cluster-index-api";
import { IColumnsType } from "component/dantd/query-form/QueryForm";
import { bytesUnitFormatter } from "../../lib/utils";
import { isOpenUp, LEVEL_MAP } from "constants/common";

const loginInfo = {
  userName: store.getState().user?.getName,
  app: store.getState().app,
};
const { RangePicker } = DatePicker;
const { confirm } = Modal;

export const getOptions = (data, type: string | number) => {
  if (!data) return [];
  const arr = Array.from(
    new Set(
      data.map((item) => {
        return item?.[type];
      })
    )
  );

  if (type === "appId") {
    const arr = [];
    data.forEach((element) => {
      let flat = false;
      if (arr.length) {
        arr.forEach((item) => {
          if (item.value === element.appId) {
            flat = true;
          }
        });
      }
      if (!flat) {
        arr.push({
          value: element.appId,
          title: element.appName,
        });
      }
    });
    return arr;
  }

  const options = arr.map((item) => ({
    title: item,
    value: item,
  }));

  return options;
};

export const getPhyClusterQueryXForm = (data: IOpPhysicsCluster[]) => {
  const formMap = [
    {
      dataIndex: "currentAppAuth",
      title: "????????????",
      type: "select",
      options: ClusterAuth,
      placeholder: "?????????",
    },
    {
      dataIndex: "health",
      title: "????????????",
      type: "select",
      options: ClusterStatus,
      placeholder: "?????????",
    },
    {
      dataIndex: "cluster",
      title: "????????????",
      type: "input",
      placeholder: "?????????",
      componentProps: {
        autocomplete: "off",
      },
    },
    {
      dataIndex: "esVersion",
      title: "??????",
      type: "select",
      options: getOptions(data, "esVersion"),
      placeholder: "?????????",
    },
  ] as IColumnsType[];
  return formMap;
};

export const getLogicClusterQueryXForm = (data: IOpLogicCluster[]) => {
  const formMap = [
    {
      dataIndex: "authType",
      title: "????????????",
      type: "select",
      options: ClusterAuth,
      placeholder: "?????????",
    },
    {
      dataIndex: "health",
      title: "????????????",
      type: "select",
      options: ClusterStatus,
      placeholder: "?????????",
    },
    {
      dataIndex: "name",
      title: "????????????",
      type: "input",
      placeholder: "?????????",
    },
    {
      dataIndex: "type",
      title: "????????????",
      type: "select",
      options: logicClusterType,
      placeholder: "?????????",
    },
    // {
    //   dataIndex: "esClusterVersions",
    //   title: "????????????",
    //   type: "select",
    //   options: getOptions(data, "esClusterVersions"),
    //   placeholder: "?????????",
    // },
    {
      dataIndex: "appId",
      title: "????????????",
      type: "select",
      options: getOptions(data, "appId"),
      placeholder: "?????????",
    },
  ] as IColumnsType[];
  return formMap;
};

export const getPhysicsBtnList = (record: IOpPhysicsCluster, setModalId: any, setDrawerId: any, reloadDataFn): ITableBtn[] => {
  let btn = [
    {
      label: "??????",
      type: "primary",
      isOpenUp: isOpenUp,
      clickFunc: () => {
        setModalId("upgradeCluster", record, reloadDataFn);
      },
    },
    {
      label: "?????????",
      type: "primary",
      isOpenUp: isOpenUp,
      clickFunc: () => {
        if (record.type === 3) {
          setModalId("dockerExpandShrinkCluster", record, reloadDataFn);
        } else if (record.type === 4) {
          setModalId("expandShrinkCluster", record, reloadDataFn);
        }
      },
    },
    {
      label: "??????",
      type: "primary",
      isOpenUp: isOpenUp,
      clickFunc: () => {
        setModalId("restartCluster", record, reloadDataFn);
      },
    },
    // {
    //   label: "??????",
    //   type: "primary",
    //   isOpenUp: isOpenUp,
    //   clickFunc: () => {
    //     setDrawerId("physicsClusterTaskDrawer", record, reloadDataFn);
    //   },
    // },
    {
      label: "??????",
      type: "primary",
      clickFunc: () => {
        setModalId("editPhyCluster", record, reloadDataFn);
      },
    },
    {
      label: "??????",
      needConfirm: true,
      confirmText: "??????",
      isOpenUp: isOpenUp,
      clickFunc: () => {
        setModalId("deleteCluster", record, reloadDataFn);
      },
    },
  ];
  if (record.currentAppAuth !== 1 && record.currentAppAuth !== 0) {
    btn = [];
  }
  return btn;
};

export const getPhysicsColumns = (setModalId: any, setDrawerId: any, reloadDataFn: any) => {
  const columns = [
    {
      title: "??????ID",
      dataIndex: "id",
      key: "id",
    },
    {
      title: "????????????",
      dataIndex: "cluster",
      key: "cluster",
      width: 180,
      render: (text: string, record: IOpPhysicsCluster) => {
        return (
          <NavRouterLink
            needToolTip={true}
            element={text}
            href={`/cluster/physics/detail?physicsCluster=${record.cluster}&physicsClusterId=${record.id}&type=${record.type}&auth=${record.currentAppAuth}#info`}
          />
        );
      },
    },
    {
      title: "????????????",
      dataIndex: "health",
      key: "health",
      render: (health: number) => {
        return (
          <div>
            <Tag className={`tag ${StatusMap[health]}`} color={StatusMap[health]}>{StatusMap[health]}</Tag>
          </div>
        );
      },
    },
    {
      title: "????????????",
      dataIndex: "type",
      key: "type",
      render: (type: number) => {
        return <div>{VERSION_MAINFEST_TYPE[type]}</div>;
      },
    },
    {
      title: "????????????",
      dataIndex: "esVersion",
      key: "esVersion",
      render: (text: string) => text || "-",
    },
    {
      title: "???????????????",
      dataIndex: "diskInfo",
      key: "diskInfo",
      sorter: true,
      render: (diskInfo) => {
        const num = Number((diskInfo.diskUsagePercent * 100).toFixed(2));
        let strokeColor;
        let yellow = "#eaaa50";
        let red = "#df6d62";
        if (num > 90) {
          strokeColor = red;
        } else if (num > 70) {
          strokeColor = yellow;
        }

        return (
          <div style={{ position: "relative" }} className="process-box">
            <Progress percent={num} size="small" strokeColor={strokeColor} width={150} />
            <div style={{ position: "absolute", fontSize: "1em" }}>
              {bytesUnitFormatter(diskInfo.diskUsage || 0)}/{bytesUnitFormatter(diskInfo.diskTotal || 0)}
            </div>
          </div>
        );
      },
    },
    {
      title: "???????????????",
      dataIndex: "activeShardNum",
      key: "activeShardNum",
      sorter: true,
    },
    {
      title: "??????",
      dataIndex: "currentAppAuth",
      key: "currentAppAuth",
      render: (text: string) => {
        return ClusterAuthMaps[text];
      },
    },
    {
      title: "??????",
      dataIndex: "desc",
      key: "desc",
      width: "8%",
      onCell: () => ({
        style: { ...cellStyle, maxWidth: 100 },
      }),
      render: (text: string) => {
        return (
          <Tooltip placement="bottomLeft" title={text}>
            {text ? text : "-"}
          </Tooltip>
        );
      },
    },
    {
      title: "??????",
      dataIndex: "operation",
      key: "operation",
      width: 180,
      fixed: 'right',
      render: (id: number, record: IOpPhysicsCluster) => {
        const btns = getPhysicsBtnList(record, setModalId, setDrawerId, reloadDataFn);
        return renderOperationBtns(btns, record);
      },
    },
  ];
  return columns;
};

export const delLogicCluster = (data: IOpLogicCluster, reloadDataFn, setModalId?: Function, url?) => {
  setModalId("deleteLogicCluster", { ...data, url: url }, reloadDataFn);
  // confirm({
  //   title: `????????????????????????${data.name}`,
  //   icon: <InfoCircleOutlined />,
  //   content: `??????????????????????????????????????????????????????????????????????????????`,
  //   width: 500,
  //   okText: "??????",
  //   cancelText: "??????",
  //   onOk() {
  //     const params: IWorkOrder = {
  //       contentObj: {
  //         id: data.id,
  //         name: data.name,
  //         type: data.type,
  //         responsible: data.responsible,
  //       },
  //       submitorAppid: loginInfo.app.appInfo()?.id,
  //       submitor: loginInfo.userName("domainAccount"),
  //       description: "",
  //       type: "logicClusterDelete",
  //     };
  //     return submitWorkOrder(params, () => {
  //       message.success("??????????????????");
  //       if (url) {
  //         url();
  //       } else {
  //         reloadDataFn();
  //       }
  //     });
  //   },
  // });
};

const getLogicBtnList = (record: IOpLogicCluster | any, fn: any, reloadDataFn: any): ITableBtn[] => {
  let btn = [
    {
      label: "??????",
      clickFunc: () => {
        fn("editCluster", record, reloadDataFn);
      },
    },
    {
      label: "?????????",
      isOpenUp: isOpenUp,
      clickFunc: () => {
        fn("expandShrink", record, reloadDataFn);
      },
    },
    {
      label: "??????",
      isOpenUp: isOpenUp,
      clickFunc: () => {
        fn("transferCluster", record, reloadDataFn);
      },
    },
    {
      label: "??????",
      needConfirm: true,
      confirmText: "??????",
      clickFunc: (record: IOpLogicCluster) => {
        delLogicCluster(record, reloadDataFn, fn);
      },
    },
  ];
  if (ClusterAuthMaps[record?.authType] === "?????????") {
    btn = [
      {
        label: "????????????",
        clickFunc: () => {
          fn("applyAauthority", record, reloadDataFn);
        },
      },
    ];
  }

  if (ClusterAuthMaps[record?.authType] === "??????") {
    btn = [
      {
        label: "????????????",
        clickFunc: () => {
          confirm({
            title: `??????`,
            icon: <InfoCircleOutlined />,
            content: `?????????????????????????`,
            width: 500,
            okText: "??????",
            cancelText: "??????",
            onOk() {
              updateBinCluster(record.authId).then(() => {
                message.success("??????????????????");
                reloadDataFn();
              });
            },
          });
        },
      },
    ];
  }

  return btn as ITableBtn[];
};

export const getLogicColumns = (tableData: IOpLogicCluster[], fn: any, reloadDataFn: any) => {
  const columns = [
    {
      title: "??????ID",
      dataIndex: "id",
      key: "id",
    },
    {
      title: "????????????",
      dataIndex: "name",
      key: "name",
      width: 180,
      render: (text: string, record: IOpLogicCluster) => {
        return (
          <NavRouterLink needToolTip={true} element={text} href={`/cluster/logic/detail?clusterId=${record.id}&type=${record.type}#info`} />
        );
      },
    },
    {
      title: () => {
        return (
          <>
            {/* {nounClusterStatus} */}
            ????????????
          </>
        );
      },
      dataIndex: "health",
      key: "status",
      width: "12%",
      render: (health) => {
        return (
          <div>
            <Tag color={StatusMap[health]}>{StatusMap[health]}</Tag>
          </div>
        );
      },
    },
    {
      title: () => {
        return (
          <>
            {/* {nounClusterType}  */}
            ????????????
          </>
        );
      },
      dataIndex: "type",
      key: "type",
      width: "12%",
      render: (type: number) => {
        return <>{clusterTypeMap[type] || "-"}</>;
      },
    },
    {
      title: "????????????",
      dataIndex: "level",
      key: "level",
      sorter: true,
      render: (text) => {
        return LEVEL_MAP[Number(text) - 1]?.label || "-";
      },
    },
    {
      title: "????????????????????????",
      dataIndex: "phyClusterAssociated",
      key: "phyClusterAssociated",
      render: (isBin: boolean) => {
        return <>{isBin ? "???" : "???"}</>;
      },
    },
    {
      title: "???????????????",
      dataIndex: "dataNodesNumber",
      key: "dataNodesNumber",
      render: (podNumber: string) => {
        return <>{podNumber != null ? podNumber : "-"}</>;
      },
    },
    {
      title: "????????????",
      dataIndex: "appName",
      key: "appName",
      render: (appId: string) => {
        return <>{appId ? appId : "-"}</>;
      },
    },
    {
      title: "??????",
      dataIndex: "memo",
      key: "memo",
      width: "8%",
      onCell: () => ({
        style: { ...cellStyle, maxWidth: 100 },
      }),
      render: (text: string) => {
        return (
          <Tooltip placement="bottomLeft" title={text}>
            {text ? text : "-"}
          </Tooltip>
        );
      },
    },
    {
      title: "??????",
      dataIndex: "authType",
      key: "authType",
      render: (podNumber: number) => {
        return <>{podNumber ? ClusterAuthMaps[podNumber] : "?????????"}</>;
      },
    },
    {
      title: "??????",
      dataIndex: "operation",
      key: "operation",
      width: 180,
      fixed: 'right',
      render: (id: number, record: IOpLogicCluster) => {
        const btns = getLogicBtnList(record, fn, reloadDataFn);
        return renderOperationBtns(btns, record);
      },
    },
  ];
  return columns;
};

export const getVersionsColumns = (fn, reloadDataFn) => {
  const getOperationList = (record, fn, reloadDataFn) => {
    return [
      {
        label: "??????",
        isOpenUp: isOpenUp,
        clickFunc: () => {
          fn("addPackageModal", record, reloadDataFn);
        },
      },
      {
        label: "??????",
        isOpenUp: isOpenUp,
        clickFunc: () => {
          confirm({
            title: "???????????????",
            icon: <InfoCircleOutlined />,
            content: "",
            width: 500,
            okText: "??????",
            cancelText: "??????",
            onOk() {
              delPackage(record.id).then((res) => {
                notification.success({ message: "????????????" });
                reloadDataFn();
              });
            },
          });
        },
      },
    ];
  };

  const cols = [
    {
      title: "ID",
      dataIndex: "id",
      key: "ID",
      width: "8%",
      sorter: (a: IVersions, b: IVersions) => a.id - b.id,
    },
    {
      title: "?????????",
      dataIndex: "esVersion",
      key: "esVersion",
    },
    {
      title: "????????????",
      dataIndex: "packageType",
      key: "packageType",
      render: (text: number) => {
        let str = "-";
        if (text == 1) {
          str = "??????????????????";
        }
        if (text == 2) {
          str = "????????????";
        }
        return str;
      },
    },
    {
      title: "url",
      dataIndex: "url",
      key: "url",
      onCell: () => ({
        style: {
          maxWidth: 200,
          ...cellStyle,
        },
      }),
      render: (url: string) => {
        return (
          <Tooltip placement="bottomLeft" title={url}>
            {url}
          </Tooltip>
        );
      },
    },
    {
      title: "??????",
      dataIndex: "manifest",
      key: "manifest",
      width: "15%",
      render: (manifest: number) => {
        return <>{VERSION_MAINFEST_TYPE[manifest] || ""}</>;
      },
    },
    {
      title: "??????",
      dataIndex: "desc",
      key: "desc",
      onCell: () => ({
        style: {
          maxWidth: 200,
          ...cellStyle,
        },
      }),
      render: (desc: string) => {
        return (
          <>
            <Tooltip placement="bottomLeft" title={desc}>
              {desc || "_"}
            </Tooltip>
          </>
        );
      },
    },
    {
      title: "?????????",
      dataIndex: "creator",
      key: "creator",
      width: "15%",
    },
    {
      title: "????????????",
      dataIndex: "createTime",
      key: "createTime",
      width: "15%",
      sorter: (a: IVersions, b: IVersions) => new Date(b.createTime).getTime() - new Date(a.createTime).getTime(),
      render: (t: number) => moment(t).format(timeFormat),
    },
    {
      title: "??????",
      dataIndex: "operation",
      key: "operation",
      render: (text: string, record: IVersions) => {
        const btns = getOperationList(record, fn, reloadDataFn);
        return renderOperationBtns(btns, record);
      },
    },
  ];

  return cols;
};

export const getEditionQueryXForm = (data) => {
  const formMap = [
    {
      dataIndex: "manifest",
      title: "????????????",
      type: "select",
      options: PHY_CLUSTER_TYPE,
      placeholder: "?????????",
    },
    {
      dataIndex: "esVersion",
      title: "?????????",
      type: "select",
      options: getOptions(data, "esVersion"),
      placeholder: "?????????",
    },
    {
      dataIndex: "createTime",
      title: "????????????",
      type: "custom",
      component: <RangePicker showTime={{ format: "HH:mm" }} format="YYYY-MM-DD HH:mm" />,
    },
  ] as IColumnsType[];
  return formMap;
};
