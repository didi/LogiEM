import * as React from "react";
import { getInfoRenderItem, getAddConfigInfoColumns } from "./config";
import { timeFormat } from "constants/time";
import Url from "lib/url-parser";
import moment from "moment";
import { connect } from "react-redux";
import { Dispatch } from "redux";
import * as actions from "actions";
import { PHY_NODE_TYPE, VERSION_MAINFEST_TYPE } from "constants/status-map";
import "./index.less";
import { PageHeader, Button, Descriptions, Steps, Divider, Table, Tooltip, Spin, message}  from 'antd';
import { IOrderInfo, ITypeEnums } from "typesPath/cluster/order-types";
import { IUser } from "typesPath/user-types";
import { IRoleIpList } from "typesPath/cluster/cluster-types";
import { cancelOrder, getOrderDetail, getTypeEnums } from "api/order-api";
import { IStringMap } from "interface/common";
const { Step } = Steps;

const mapDispatchToProps = (dispatch: Dispatch) => ({
  setModalId: (modalId: string, params?: any, cb?: Function) =>
    dispatch(actions.setModalId(modalId, params, cb)),
  setDrawerId: (modalId: string, params?: any, cb?: Function) =>
    dispatch(actions.setDrawerId(modalId, params, cb)),
});
const connects: any = connect;
@connects(null, mapDispatchToProps)
export class OrderDetail extends React.Component<{
  setModalId: Function;
  setDrawerId: Function;
}> {
  public result: boolean;
  public orderId: number;

  public state = {
    showUnfold: false,
    loading: false,
    typeEnums: {} as IStringMap,
    orderInfo: {} as IOrderInfo,
  };

  constructor(props) {
    super(props);
    const url = Url();
    this.orderId = Number(url.search.orderId);
  }

  public dealInfo(info: IOrderInfo) {
    info = JSON.parse(JSON.stringify(info));

    if (info.detail) {
      info.detailInfo = JSON.parse(info.detail);
    }

    const { approverList = [] } = info;
    info.approvers = [];

    approverList?.forEach((item) => {
      if (item.name) {
        info.approvers.push(item.name);
      }
    });
    info.applicant = info.applicant || ({} as IUser);
    info.currentStep =
      info.status === 0 ? 1 : info.status === 1 || info.status === 2 ? 2 : 0; // 0????????? 1????????? 2????????? 3?????????
    info.gmtCreate = moment(info.gmtCreate).format(timeFormat);
    return info;
  }

  public renderDetail() {
    const info = this.dealInfo(this.state.orderInfo);
    const infoList = getInfoRenderItem(info, this.result);
    const type = this.state.orderInfo.type;
    const title = this.state.orderInfo.title;
    const roleOrderArr =
      info.detailInfo?.roleOrder && JSON.parse(info.detailInfo?.roleOrder);
    let { detail } = info;
    let newEsConfigs = [];
    let originalConfigs = [];
    let text = "??????????????????:";
    if (JSON.parse(detail)) {
      detail = JSON.parse(detail);
      newEsConfigs = detail?.newEsConfigs;
      originalConfigs = detail?.originalConfigs;
    }
    newEsConfigs = newEsConfigs?.map((item: any, index: number) => {
      return { ...item, index };
    });
    originalConfigs = originalConfigs?.map((item: any, index: number) => {
      return { ...item, index };
    });
    if (title.indexOf("??????") > -1) {
      text = "??????????????????:";
    } else if (title.indexOf("??????") > -1) {
      text = "???????????????:";
    }

    return (
      <>
        <Descriptions
          title={`????????????-${this.state.typeEnums[type] || ""}`}
          column={3}
        >
          {infoList &&
            infoList.map((item, key) => (
              <Descriptions.Item key={key} label={item.label}>
                <Tooltip placement="bottomLeft" title={item.value}>
                  <span>{item.value || '-'}</span>
                </Tooltip>
              </Descriptions.Item>
            ))}
        </Descriptions>
        {info.type === "clusterOpUpdate" ||
          info.type === "clusterOpRestart" ||
          info.type === "clusterOpConfigRestart" ? (
          <Descriptions column={1}>
            <Descriptions.Item label="????????????">
              <Tooltip
                placement="bottomLeft"
                title={roleOrderArr?.join(" --> ")}
              >
                {roleOrderArr.map((ele: string, index: number) => (
                  <span key={index}>
                    <span className="txt">{ele}</span>
                    {roleOrderArr?.length > index + 1 ? (
                      <span>&nbsp;&nbsp; --&gt; &nbsp;&nbsp;</span>
                    ) : null}
                  </span>
                ))}
              </Tooltip>
            </Descriptions.Item>
          </Descriptions>
        ) : null}
        <Descriptions column={1}>
          <Descriptions.Item label="????????????">
            <Tooltip placement="bottomLeft" title={info.description}>
              <span>{info.description}</span>
            </Tooltip>
          </Descriptions.Item>
        </Descriptions>
        {info.type === "clusterOpConfigRestart" ? (
          <div>
            <div>{text}</div>
            <Table
              rowKey="index"
              dataSource={
                // originalConfigs.length ? originalConfigs : 
                newEsConfigs
              }
              columns={getAddConfigInfoColumns(
                info.title,
                this.props.setDrawerId
              )}
            />
          </div>
        ) : (
          ""
        )}
        {info.type === "clusterOpIndecrease" ? this.getNodeList(info) : null}
      </>
    );
  }

  public getNodes = (role: string, data: IRoleIpList[]) => {
    const text = data
      ? data
        ?.filter((ele: IRoleIpList) => {
          let r = ele.role;
          if (ele.beCold) {
            r = 'coldnode'
          }
          return r === role
        })
        ?.map((e: IRoleIpList) => e.hostname)
        ?.join("; ")
      : "";
    return text;
  };

  public getNodeHost = (origin: IRoleIpList[], data: IRoleIpList[]) => {
    const hostData = PHY_NODE_TYPE.map((role) => {
      return {
        role,
        originHostNames: this.getNodes(role, origin) || "--",
        hostnames: this.getNodes(role, data) || "--",
      };
    });
    return hostData;
  };

  public getNodeList(data?: IOrderInfo) {
    const text = data?.detailInfo?.operationType === 2 ? "??????" : "??????"; //  2:?????? 3:??????
    let nodeColumns = [];
    let nodeSource = [];
    if (data?.detailInfo?.type === 3) {
      // type 3:docker 4:host
      const dockerColumns = [
        {
          title: "????????????",
          dataIndex: "role",
          key: "role",
        },
        {
          title: "??????????????????",
          dataIndex: "originPodNumber",
          key: "originPodNumber",
        },
        {
          title: `${text}????????????`,
          dataIndex: "podNumber",
          key: "podNumber",
        },
        {
          title: "????????????",
          dataIndex: "machineSpec",
          key: "machineSpec",
        },
      ];
      nodeColumns = dockerColumns;
      nodeSource = data?.detailInfo?.roleClusters;
    } else {
      const hostColumns = [
        {
          title: "????????????",
          dataIndex: "role",
          key: "role",
        },
        {
          title: "??????????????????",
          dataIndex: "originHostNames",
          key: "originHostNames",
        },
        {
          title: `${text}????????????`,
          dataIndex: "hostnames",
          key: "hostnames",
        },
      ];
      nodeColumns = hostColumns;
      const nodeHostData = this.getNodeHost(
        data?.detailInfo?.originRoleClusterHosts,
        data?.detailInfo?.roleClusterHosts
      );
      nodeSource = nodeHostData;
    }

    return (
      <>
        <h6>{VERSION_MAINFEST_TYPE[data?.detailInfo?.type]}??????</h6>
        <Table
          rowKey="role"
          dataSource={nodeSource}
          columns={nodeColumns}
          pagination={false}
        />
      </>
    );
  }

  public getOpinion(info: IOrderInfo) {
    return <>{moment(info.finishTime).format(timeFormat)}</>;
  }

  public getApprovalList(info: string[]) {
    // ?????????????????????????????????
    const eleList = info.map((item) => <p>{item}</p>);
    return <></>;
  }

  public componentDidMount() {
    this.getOrderDetail();
    this.getTypeEnumsFn();
  }

  public getOrderDetail = () => {
    this.setState({ loading: true });
    getOrderDetail(this.orderId).then((data) => {
      data =
        data ||
        ({
          applicant: {},
          detail: {},
          approverList: [],
        } as IOrderInfo);
      this.setState({
        orderInfo: data,
        loading: false,
      });
    });
  };

  public getTypeEnumsFn = () => {
    getTypeEnums().then((res) => {
      const arr = res.map((ele, index) => {
        return {
          ...ele,
          value: ele.type,
          title: ele.message,
          key: index + 1,
        };
      });
      const obj = {} as IStringMap;
      arr.map((e: ITypeEnums) => {
        obj[e.type] = e.message;
      });

      this.setState({
        typeEnums: obj,
      });
    });
  };

  public cancelOrder(orderId: number) {
    return cancelOrder(orderId).then(() => {
      message.success("????????????");
      this.getOrderDetail();
    });
  }

  public handerUnfold = () => {
    this.setState({
      showUnfold: !this.state.showUnfold,
    });
  };

  public renderApprovers = (info: string[]) => {
    const arrApprovers = [] as any[];
    let arrItem = [] as string[];
    info.forEach((item, index) => {
      arrItem.push(item);
      if (arrItem.length === 3) {
        arrApprovers.push(arrItem);
        arrItem = [];
      }
    });
    if (arrItem.length > 0) {
      arrApprovers.push(arrItem);
      arrItem = [];
    }
    let unfoldList = [];
    if (arrApprovers.length > 2) {
      unfoldList = arrApprovers.splice(2);
    }
    return (
      <div>
        {arrApprovers.map((item, index) => {
          return (
            <>
              <div key={index}>{item.toString()}</div>
            </>
          );
        })}
        {this.state.showUnfold
          ? unfoldList.map((item, index) => {
            return (
              <>
                <div key={index + "un"}>{item.toString()}</div>
              </>
            );
          })
          : null}
        <a onClick={this.handerUnfold}>
          {this.state.showUnfold ? "??????" : "??????"}
        </a>
      </div>
    );
  };

  public render() {
    const isOrder = window.location.href.includes("my-application");
    const info = this.dealInfo(this.state.orderInfo);
    this.result = info.approverList?.some((ele) => {
      if (ele.name === info.applicant.name) {
        return true;
      }
    });
    const urlMyApproval =
      window.location.pathname.indexOf("my-approval") > -1
        ? "/my-approval"
        : "";
    const btns = [
      {
        aHref: `/user${urlMyApproval}`,
        label: "????????????",
      },
      {
        label: "????????????",
      },
    ];
    return (
      <div className="order-detail">
        <Spin spinning={this.state.loading}>
          <div className="detail-top">
            <PageHeader
              className="btn-groups"
              title={`${info?.title || ""}???${info.id || ""}???`}
              extra={
                info.currentStep === 1 || this.result ? (
                  <>
                    {isOrder && info.status === 0 ? (
                      <span key="4">
                        <Button
                          key="5"
                          type="primary"
                          onClick={() => this.cancelOrder(info.id)}
                        >
                          ??????
                        </Button>
                      </span>
                    ) : info.status === 1 ||
                      info.status === 2 ||
                      info.status === 3 ? null : (
                      <span key="3">
                        <Button
                          key="1"
                          type="primary"
                          className="detail-btn"
                          onClick={() =>
                            this.props.setModalId(
                              "showApprovalModal",
                              { ...info, outcome: "agree" },
                              this.getOrderDetail
                            )
                          }
                        >
                          ??????
                        </Button>
                        <Button
                          key="2"
                          onClick={() =>
                            this.props.setModalId(
                              "showApprovalModal",
                              { ...info, outcome: "disagree" },
                              this.getOrderDetail
                            )
                          }
                        >
                          ??????
                        </Button>
                      </span>
                    )}
                  </>
                ) : null
              }
            />
          </div>
          <div className="work-detail-box">
            <Steps
              className="step"
              current={info.currentStep}
              status={
                info.currentStep === 2 && info.status === 2
                  ? "error"
                  : "process"
              }
              progressDot={true}
            >
              <Step
                title={`${this.state.typeEnums[info.type] || ""}${info.status === 3 ? "???????????????" : ""
                  }`}
                subTitle={info.applicant.name}
                description={moment(info.createTime).format(timeFormat)}
              />
              <Step
                title="?????????"
                description={
                  info.status === 0
                    ? this.renderApprovers(info.approvers)
                    : null
                }
              />
              <Step
                title={
                  info.status === 1
                    ? "?????????"
                    : info.status === 2
                      ? "?????????"
                      : "??????"
                }
                subTitle={
                  info.currentStep === 2
                    ? this.getApprovalList(info.approvers)
                    : null
                }
                description={
                  info.currentStep === 2 ? this.getOpinion(info) : null
                }
              />
            </Steps>
            <Divider />
            <Descriptions title="???????????????" column={2}>
              <Descriptions.Item label="?????????">
                {info.applicant.name}
              </Descriptions.Item>
              <Descriptions.Item label="????????????">
                {moment(info.createTime).format(timeFormat)}
              </Descriptions.Item>
              <Descriptions.Item label="????????????">
                {info.applicantAppName}
              </Descriptions.Item>
            </Descriptions>
            <Divider />
            {this.state.orderInfo && this.state.orderInfo.detail
              ? this.renderDetail()
              : null}
            {info.status === 1 || info.status === 2 ? (
              <>
                <Divider />
                <Descriptions title="????????????" column={1}>
                  <Descriptions.Item label="????????????">
                    {info.opinion}
                  </Descriptions.Item>
                </Descriptions>
              </>
            ) : null}
          </div>
        </Spin>
      </div>
    );
  }
}
